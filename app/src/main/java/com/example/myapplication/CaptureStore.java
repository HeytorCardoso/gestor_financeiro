package com.example.myapplication;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashSet;

/** Valores em centavos. Exclusões e capturas unidas mantêm identidade para não reaparecer. */
public final class CaptureStore extends SQLiteOpenHelper {
    public CaptureStore(Context context) { super(context, "captures.db", null, 6); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE captures (_id INTEGER PRIMARY KEY, token TEXT UNIQUE NOT NULL, raw TEXT NOT NULL, name TEXT NOT NULL, cents INTEGER NOT NULL, category TEXT NOT NULL, captured INTEGER NOT NULL, pending INTEGER NOT NULL, notes TEXT NOT NULL DEFAULT '', edited INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL DEFAULT 'Nubank', kind TEXT NOT NULL DEFAULT 'UNKNOWN', counterparty TEXT NOT NULL DEFAULT '', category_key TEXT NOT NULL DEFAULT '', category_manual INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX captures_time ON captures(captured)");
        db.execSQL("CREATE TABLE changes (revision INTEGER NOT NULL)");
        db.execSQL("INSERT INTO changes VALUES (0)");
        watch(db, "captures");
        createDecisions(db);
        createCategoryRules(db);
        createUndo(db);
    }
    private void watch(SQLiteDatabase db, String table) {
        for (String operation : new String[]{"INSERT", "UPDATE", "DELETE"})
            db.execSQL("CREATE TRIGGER " + table + "_" + operation.toLowerCase(java.util.Locale.ROOT) + " AFTER " + operation + " ON " + table + " BEGIN UPDATE changes SET revision=revision+1; END");
    }
    private void createDecisions(SQLiteDatabase db) {
        // status: 1 = união confirmada, 2 = operações diferentes / união desfeita.
        db.execSQL("CREATE TABLE duplicate_decisions (a INTEGER NOT NULL, b INTEGER NOT NULL, status INTEGER NOT NULL, kept_id INTEGER, PRIMARY KEY(a,b))");
        watch(db, "duplicate_decisions");
    }
    private void createCategoryRules(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE category_rules (identity TEXT PRIMARY KEY NOT NULL, category TEXT NOT NULL)");
        watch(db,"category_rules");
    }
    private void createUndo(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE deletion_batches (_id INTEGER PRIMARY KEY AUTOINCREMENT, expires INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE deletion_items (batch INTEGER NOT NULL, capture_id INTEGER PRIMARY KEY, previous_pending INTEGER NOT NULL)");
    }
    private String learnedCategory(SQLiteDatabase db,String key) {
        if (key.isEmpty()) return "Outros";
        try(Cursor c=db.query("category_rules",new String[]{"category"},"identity=?",new String[]{key},null,null,null)) {
            return c.moveToFirst() ? c.getString(0) : "Outros";
        }
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE captures ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE captures ADD COLUMN edited INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE captures ADD COLUMN source TEXT NOT NULL DEFAULT 'Nubank'");
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE captures ADD COLUMN kind TEXT NOT NULL DEFAULT 'UNKNOWN'");
            db.execSQL("ALTER TABLE captures ADD COLUMN counterparty TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE INDEX captures_time ON captures(captured)");
            createDecisions(db);
            try (Cursor c = db.query("captures", new String[]{"_id", "raw", "source", "cents"}, "pending IN (0,1)", null, null, null, null)) {
                while (c.moveToNext()) {
                    NotificationParser.Result parsed = parse(c.getString(2), c.getString(1));
                    if (parsed == null || (parsed.cents > 0) != (c.getLong(3) > 0)) continue;
                    ContentValues v = new ContentValues(); v.put("kind", parsed.kind.name()); v.put("counterparty", parsed.counterparty);
                    db.update("captures", v, "_id=?", new String[]{String.valueOf(c.getLong(0))});
                }
            }
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE captures ADD COLUMN category_key TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE captures ADD COLUMN category_manual INTEGER NOT NULL DEFAULT 0");
            createCategoryRules(db);
            try(Cursor c=db.query("captures",new String[]{"_id","raw","source","edited","category"},"pending IN (0,1,3)",null,null,null,null)) {
                while(c.moveToNext()) {
                    ContentValues v=new ContentValues();v.put("category_key",CategoryRules.identity(parse(c.getString(2),c.getString(1))));
                    v.put("category_manual",c.getInt(3)==1 && !c.getString(4).equals("Outros") ? 1 : 0);
                    db.update("captures",v,"_id=?",new String[]{String.valueOf(c.getLong(0))});
                }
            }
        }
            if (oldVersion < 6) createUndo(db);
    }
    private NotificationParser.Result parse(String source, String raw) {
        return source.startsWith("Gmail") ? GmailTransferParser.parse("", raw) : NotificationParser.parse(raw);
    }
    public long revision() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT revision FROM changes", null)) { c.moveToFirst(); return c.getLong(0); }
    }
    public boolean capture(String token, String raw, long time) {
        return capture(token, raw, time, NotificationParser.parse(raw), "Nubank");
    }
    public boolean capture(String token, String raw, long time, NotificationParser.Result parsed, String source) {
        ContentValues v = new ContentValues(); v.put("token", token); v.put("raw", raw); v.put("source", source);
        v.put("name", parsed == null ? "Lançamento incompleto" : parsed.name);
        v.put("cents", parsed == null ? 0 : parsed.cents); v.put("category", "Outros");
        v.put("captured", time); v.put("pending", parsed == null ? 1 : 0);
        v.put("kind", parsed == null ? TransactionKind.UNKNOWN.name() : parsed.kind.name());
        v.put("counterparty", parsed == null ? "" : parsed.counterparty);
        v.put("category_key",CategoryRules.identity(parsed));
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            v.put("category",learnedCategory(db,CategoryRules.identity(parsed)));
            boolean changed = db.insertWithOnConflict("captures", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
            // pending=2 apagado, pending=3 unido: não recriar nem sobrescrever edições.
            if (!changed && parsed != null) changed = db.update("captures", v, "token=? AND edited=0 AND (pending=1 OR (pending=0 AND raw<>?))", new String[]{token, raw}) > 0;
            db.setTransactionSuccessful(); return changed;
        } finally { db.endTransaction(); }
    }
    public ArrayList<Record> records(boolean incompleteOnly) {
        return query(incompleteOnly ? "pending=1" : "pending IN (0,1)", null);
    }
    private ArrayList<Record> query(String selection, String[] args) {
        ArrayList<Record> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("captures", null, selection, args, null, null, "captured DESC, _id DESC")) {
            while (c.moveToNext()) rows.add(read(c));
        } return rows;
    }
    private Record read(Cursor c) {
        return new Record(c.getLong(c.getColumnIndexOrThrow("_id")), c.getString(c.getColumnIndexOrThrow("name")), c.getLong(c.getColumnIndexOrThrow("cents")), c.getString(c.getColumnIndexOrThrow("category")), c.getString(c.getColumnIndexOrThrow("raw")), c.getLong(c.getColumnIndexOrThrow("captured")), c.getString(c.getColumnIndexOrThrow("notes")), c.getInt(c.getColumnIndexOrThrow("pending")) == 1, c.getString(c.getColumnIndexOrThrow("source")), TransactionKind.fromStorage(c.getString(c.getColumnIndexOrThrow("kind"))), c.getString(c.getColumnIndexOrThrow("counterparty")),c.getString(c.getColumnIndexOrThrow("category_key")),c.getInt(c.getColumnIndexOrThrow("category_manual"))==1);
    }
    public void reprocessIncomplete() {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            try (Cursor c = db.query("captures", new String[]{"_id", "raw", "source"}, "pending=1 AND edited=0", null, null, null, null)) {
                while (c.moveToNext()) {
                    NotificationParser.Result result = parse(c.getString(2), c.getString(1));
                    if (result == null) continue;
                    ContentValues v = new ContentValues(); v.put("name", result.name); v.put("cents", result.cents); v.put("pending", 0);
                    v.put("kind", result.kind.name()); v.put("counterparty", result.counterparty);
                    String key=CategoryRules.identity(result);v.put("category_key",key);v.put("category",learnedCategory(db,key));
                    db.update("captures", v, "_id=? AND pending=1 AND edited=0", new String[]{String.valueOf(c.getLong(0))});
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void edit(long id, String name, long cents, String category, String notes) {
        ArrayList<Record> existing = query("_id=? AND pending IN (0,1)", new String[]{String.valueOf(id)});
        if (existing.isEmpty()) return;
        edit(id, name, cents, category, notes, existing.get(0).kind, existing.get(0).counterparty);
    }
    public void edit(long id,String name,long cents,String category,String notes,TransactionKind kind,String counterparty) {
        edit(id,name,cents,category,notes,kind,counterparty,true);
    }
    public void edit(long id,String name,long cents,String category,String notes,TransactionKind kind,String counterparty,boolean rememberCategory) {
        if (!CategoryRules.valid(category)) throw new IllegalArgumentException("Categoria inválida");
        if (cents>0 && kind!=TransactionKind.ACCOUNT_TRANSFER && kind!=TransactionKind.UNKNOWN) throw new IllegalArgumentException("Receitas devem ser movimentações da conta");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ArrayList<Record> records=query("_id=? AND pending IN (0,1)",new String[]{String.valueOf(id)});
            if (records.isEmpty()) return;
            Record previous=records.get(0);
            boolean changedCategory=!category.equals(previous.category);
            ContentValues v=new ContentValues();v.put("name",name);v.put("cents",cents);v.put("category",category);
            v.put("notes",notes);v.put("edited",1);v.put("pending",cents==0 ? 1 : 0);v.put("kind",kind.name());v.put("counterparty",counterparty.trim());
            v.put("category_manual",previous.categoryManual || changedCategory ? 1 : 0);
            db.update("captures",v,"_id=?",new String[]{String.valueOf(id)});
            if (changedCategory && rememberCategory && !previous.categoryKey.isEmpty()) {
                ContentValues rule=new ContentValues();rule.put("identity",previous.categoryKey);rule.put("category",category);
                db.insertWithOnConflict("category_rules",null,rule,SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    private static String pairKey(long a, long b) { return Math.min(a,b) + ":" + Math.max(a,b); }
    public ArrayList<Duplicate> possibleDuplicates() {
        HashSet<String> decided = new HashSet<>();
        try (Cursor c = getReadableDatabase().query("duplicate_decisions", new String[]{"a", "b"}, null, null, null, null, null)) {
            while(c.moveToNext()) decided.add(pairKey(c.getLong(0), c.getLong(1)));
        }
        ArrayList<Record> rows = records(false); ArrayList<Duplicate> results = new ArrayList<>();
        for (int i=0; i<rows.size(); i++) for (int j=i+1; j<rows.size(); j++) {
            Record a=rows.get(i), b=rows.get(j);
            if (a.time - b.time > DuplicateMatcher.WINDOW_MS) break;
            if (a.incomplete || b.incomplete || decided.contains(pairKey(a.id,b.id))) continue;
            String reason = DuplicateMatcher.reason(a.source,a.cents,a.time,a.kind,a.counterparty,b.source,b.cents,b.time,b.kind,b.counterparty);
            if (reason != null) results.add(new Duplicate(a,b,reason));
        }
        return results;
    }
    private void decision(SQLiteDatabase db, long a, long b, int status, long keep) {
        ContentValues v = new ContentValues(); v.put("a",Math.min(a,b)); v.put("b",Math.max(a,b)); v.put("status",status); v.put("kept_id",keep);
        db.insertWithOnConflict("duplicate_decisions",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void markDifferent(long a, long b) { decision(getWritableDatabase(),a,b,2,0); }
    /** União explícita e reversível: os dois textos e as edições são preservados. */
    public boolean merge(long keep, long other) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ArrayList<Record> rows = query("_id IN (?,?) AND pending=0", new String[]{String.valueOf(keep),String.valueOf(other)});
            if (rows.size()!=2 || keep==other) return false;
            Record a=rows.get(0), b=rows.get(1);
            if (DuplicateMatcher.reason(a.source,a.cents,a.time,a.kind,a.counterparty,b.source,b.cents,b.time,b.kind,b.counterparty)==null) return false;
            ContentValues hidden = new ContentValues(); hidden.put("pending",3);
            db.update("captures",hidden,"_id=?",new String[]{String.valueOf(other)});
            ContentValues move = new ContentValues(); move.put("kept_id",keep);
            db.update("duplicate_decisions",move,"status=1 AND kept_id=?",new String[]{String.valueOf(other)});
            decision(db,keep,other,1,keep);
            db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
    public ArrayList<Record> linkedRecords(long keep) {
        return query("pending=3 AND _id IN (SELECT a FROM duplicate_decisions WHERE status=1 AND kept_id=? UNION SELECT b FROM duplicate_decisions WHERE status=1 AND kept_id=?)",new String[]{String.valueOf(keep),String.valueOf(keep)});
    }
    public void undoMerge(long keep) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            for (Record linked : linkedRecords(keep)) {
                ContentValues v=new ContentValues(); v.put("pending",0); db.update("captures",v,"_id=? AND pending=3",new String[]{String.valueOf(linked.id)});
            }
            ContentValues v=new ContentValues(); v.put("status",2);
            db.update("duplicate_decisions",v,"kept_id=? AND status=1",new String[]{String.valueOf(keep)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    /** Atualiza somente os detalhes; preserva os valores mais recentes da captura. */
    public boolean editDetails(long id, String category, String notes, boolean remember) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ArrayList<Record> rows=query("_id=? AND pending IN (0,1)",new String[]{String.valueOf(id)});
            if(rows.isEmpty()) return false;
            Record r=rows.get(0);
            edit(id,r.name,r.cents,category,notes,r.kind,r.counterparty,remember);
            db.setTransactionSuccessful();return true;
        } finally {db.endTransaction();}
    }
    public static final long UNDO_WINDOW_MS=10000;
    public static final class Deletion {
        public final long id,expires;
        Deletion(long id,long expires) {this.id=id;this.expires=expires;}
    }
    /** Preserva os dados e os vínculos durante a janela de recuperação. */
    public boolean deleteWithUndo(long id,long now) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ArrayList<Record> rows=query("_id=? AND pending IN (0,1)",new String[]{String.valueOf(id)});
            if(rows.isEmpty()) return false;
            ArrayList<Record> linked=linkedRecords(id);linked.add(rows.get(0));
            ContentValues batch=new ContentValues();batch.put("expires",now+UNDO_WINDOW_MS);
            long batchId=db.insertOrThrow("deletion_batches",null,batch);
            for(Record r:linked) {
                ContentValues item=new ContentValues();item.put("batch",batchId);item.put("capture_id",r.id);
                item.put("previous_pending",r.id==id ? (r.incomplete ? 1 : 0) : 3);
                db.insertOrThrow("deletion_items",null,item);
                ContentValues hidden=new ContentValues();hidden.put("pending",2);
                db.update("captures",hidden,"_id=?",new String[]{String.valueOf(r.id)});
            }
            db.setTransactionSuccessful();return true;
        } finally {db.endTransaction();}
    }
    public Deletion latestDeletion(long now) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT _id,expires FROM deletion_batches WHERE expires>? ORDER BY _id DESC LIMIT 1",new String[]{String.valueOf(now)})) {
            return c.moveToFirst() ? new Deletion(c.getLong(0),c.getLong(1)) : null;
        }
    }
    public boolean undoDeletion(long batch,long now) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            try(Cursor c=db.rawQuery("SELECT expires FROM deletion_batches WHERE _id=?",new String[]{String.valueOf(batch)})) {
                if(!c.moveToFirst() || c.getLong(0)<=now) return false;
            }
            try(Cursor c=db.rawQuery("SELECT capture_id,previous_pending FROM deletion_items WHERE batch=?",new String[]{String.valueOf(batch)})) {
                while(c.moveToNext()) {
                    ContentValues v=new ContentValues();v.put("pending",c.getInt(1));
                    db.update("captures",v,"_id=? AND pending=2",new String[]{String.valueOf(c.getLong(0))});
                }
            }
            removeDeletion(db,batch);db.setTransactionSuccessful();return true;
        } finally {db.endTransaction();}
    }
    public void purgeExpiredDeletions(long now) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ArrayList<Long> expired=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT _id FROM deletion_batches WHERE expires<=?",new String[]{String.valueOf(now)})) {
                while(c.moveToNext()) expired.add(c.getLong(0));
            }
            for(long batch:expired) {
                try(Cursor c=db.rawQuery("SELECT capture_id FROM deletion_items WHERE batch=?",new String[]{String.valueOf(batch)})) {
                    while(c.moveToNext()) clear(db,c.getLong(0));
                }
                removeDeletion(db,batch);
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    private void removeDeletion(SQLiteDatabase db,long batch) {
        String[] args={String.valueOf(batch)};
        db.delete("deletion_items","batch=?",args);db.delete("deletion_batches","_id=?",args);
    }
    public void discard(long id) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            ArrayList<Record> linked=linkedRecords(id);
            clear(db,id); for (Record r:linked) clear(db,r.id);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private void clear(SQLiteDatabase db,long id) {
        ContentValues v = new ContentValues(); v.put("pending",2); v.put("raw",""); v.put("notes",""); v.put("name","Lançamento apagado"); v.put("cents",0); v.put("category","Outros"); v.put("counterparty",""); v.put("kind",TransactionKind.UNKNOWN.name());v.put("category_key","");
        db.update("captures",v,"_id=?",new String[]{String.valueOf(id)});
    }
    public static final class Duplicate {
        public final Record first, second; public final String reason;
        Duplicate(Record first,Record second,String reason) { this.first=first; this.second=second; this.reason=reason; }
    }
    public static final class Record {
        public final long id,cents,time; public final String name,category,raw,notes,source,counterparty,categoryKey; public final boolean incomplete,categoryManual; public final TransactionKind kind;
        Record(long id,String name,long cents,String category,String raw,long time,String notes,boolean incomplete,String source,TransactionKind kind,String counterparty,String categoryKey,boolean categoryManual) {
            this.categoryKey=categoryKey;this.categoryManual=categoryManual;this.id=id; this.name=name; this.cents=cents; this.category=category; this.raw=raw; this.time=time; this.notes=notes; this.incomplete=incomplete; this.source=source; this.kind=kind; this.counterparty=counterparty;
        }
    }
}
