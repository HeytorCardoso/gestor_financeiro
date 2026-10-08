package com.example.myapplication;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;

/** Banco privado do app. Valores monetários são armazenados em centavos. */
public final class CaptureStore extends SQLiteOpenHelper {
    public CaptureStore(Context context) { super(context, "captures.db", null, 3); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE captures (_id INTEGER PRIMARY KEY, token TEXT UNIQUE NOT NULL, raw TEXT NOT NULL, name TEXT NOT NULL, cents INTEGER NOT NULL, category TEXT NOT NULL, captured INTEGER NOT NULL, pending INTEGER NOT NULL, notes TEXT NOT NULL DEFAULT '', edited INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL DEFAULT 'Nubank')");
        db.execSQL("CREATE TABLE changes (revision INTEGER NOT NULL)");
        db.execSQL("INSERT INTO changes VALUES (0)");
        for (String operation : new String[]{"INSERT", "UPDATE", "DELETE"})
            db.execSQL("CREATE TRIGGER captures_" + operation.toLowerCase(java.util.Locale.ROOT) + " AFTER " + operation + " ON captures BEGIN UPDATE changes SET revision=revision+1; END");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE captures ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE captures ADD COLUMN edited INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE captures ADD COLUMN source TEXT NOT NULL DEFAULT 'Nubank'");
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
        SQLiteDatabase db = getWritableDatabase();
        if (db.insertWithOnConflict("captures", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1) return true;
        // Atualizações enriquecem capturas automáticas, sem recriar exclusões nem sobrescrever edições.
        if (parsed != null) return db.update("captures", v, "token=? AND edited=0 AND (pending=1 OR (pending=0 AND raw<>?))", new String[]{token, raw}) > 0;
        return false;
    }
    public ArrayList<Record> records(boolean pending) {
        ArrayList<Record> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("captures", null, pending ? "pending=1" : "pending IN (0,1)", null, null, null, "captured DESC, _id DESC")) {
            while(c.moveToNext()) rows.add(new Record(c.getLong(c.getColumnIndexOrThrow("_id")), c.getString(c.getColumnIndexOrThrow("name")), c.getLong(c.getColumnIndexOrThrow("cents")), c.getString(c.getColumnIndexOrThrow("category")), c.getString(c.getColumnIndexOrThrow("raw")), c.getLong(c.getColumnIndexOrThrow("captured")), c.getString(c.getColumnIndexOrThrow("notes")), c.getInt(c.getColumnIndexOrThrow("pending")) == 1, c.getString(c.getColumnIndexOrThrow("source"))));
        } return rows;
    }
    /** Reaproveita o texto salvo com o novo extrator; nunca sobrescreve edições manuais. */
    public void reprocessIncomplete() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.query("captures", new String[]{"_id", "raw", "source"}, "pending=1 AND edited=0", null, null, null, null)) {
                while (c.moveToNext()) {
                    String raw = c.getString(1);
                    NotificationParser.Result result = c.getString(2).startsWith("Gmail") ? GmailTransferParser.parse("", raw) : NotificationParser.parse(raw);
                    if (result == null) continue;
                    ContentValues v = new ContentValues(); v.put("name", result.name); v.put("cents", result.cents); v.put("pending", 0);
                    db.update("captures", v, "_id=? AND pending=1 AND edited=0", new String[]{String.valueOf(c.getLong(0))});
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void edit(long id, String name, long cents, String category, String notes) {
        ContentValues v = new ContentValues(); v.put("name", name); v.put("cents", cents);
        v.put("category", category); v.put("notes", notes); v.put("edited", 1); v.put("pending", cents == 0 ? 1 : 0);
        getWritableDatabase().update("captures", v, "_id=?", new String[]{String.valueOf(id)});
    }
    public void discard(long id) { // Mantém o token para que a mesma notificação não volte a ser importada.
        ContentValues v = new ContentValues(); v.put("pending", 2); v.put("raw", ""); v.put("notes", ""); v.put("name", "Lançamento apagado"); v.put("cents", 0); v.put("category", "Outros"); getWritableDatabase().update("captures", v, "_id=?", new String[]{String.valueOf(id)});
    }
    public static final class Record {
        public final long id, cents, time; public final String name, category, raw, notes, source; public final boolean incomplete;
        Record(long id, String name, long cents, String category, String raw, long time, String notes, boolean incomplete, String source) { this.id=id; this.name=name; this.cents=cents; this.category=category; this.raw=raw; this.time=time; this.notes=notes; this.incomplete=incomplete; this.source=source; }
    }
}
