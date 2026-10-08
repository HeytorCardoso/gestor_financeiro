package com.example.myapplication;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;

/** Banco privado do app. Valores monetários são armazenados em centavos. */
public final class CaptureStore extends SQLiteOpenHelper {
    public CaptureStore(Context context) { super(context, "captures.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE captures (_id INTEGER PRIMARY KEY, token TEXT UNIQUE NOT NULL, raw TEXT NOT NULL, name TEXT NOT NULL, cents INTEGER NOT NULL, category TEXT NOT NULL, captured INTEGER NOT NULL, pending INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE changes (revision INTEGER NOT NULL)");
        db.execSQL("INSERT INTO changes VALUES (0)");
        for (String operation : new String[]{"INSERT", "UPDATE", "DELETE"})
            db.execSQL("CREATE TRIGGER captures_" + operation.toLowerCase(java.util.Locale.ROOT) + " AFTER " + operation + " ON captures BEGIN UPDATE changes SET revision=revision+1; END");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    public long revision() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT revision FROM changes", null)) { c.moveToFirst(); return c.getLong(0); }
    }
    public boolean capture(String token, String raw, long time) {
        NotificationParser.Result parsed = NotificationParser.parse(raw);
        ContentValues v = new ContentValues(); v.put("token", token); v.put("raw", raw);
        v.put("name", parsed == null ? "Notificação para revisar" : parsed.name);
        v.put("cents", parsed == null ? 0 : parsed.cents); v.put("category", "Outros");
        v.put("captured", time); v.put("pending", parsed == null ? 1 : 0);
        SQLiteDatabase db = getWritableDatabase();
        if (db.insertWithOnConflict("captures", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1) return true;
        // Algumas notificações chegam primeiro sem conteúdo e são expandidas depois.
        if (parsed != null) return db.update("captures", v, "token=? AND pending=1", new String[]{token}) > 0;
        return false;
    }
    public ArrayList<Record> records(boolean pending) {
        ArrayList<Record> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("captures", null, "pending=?", new String[]{pending ? "1" : "0"}, null, null, "captured DESC, _id DESC")) {
            while(c.moveToNext()) rows.add(new Record(c.getLong(c.getColumnIndexOrThrow("_id")), c.getString(c.getColumnIndexOrThrow("name")), c.getLong(c.getColumnIndexOrThrow("cents")), c.getString(c.getColumnIndexOrThrow("category")), c.getString(c.getColumnIndexOrThrow("raw")), c.getLong(c.getColumnIndexOrThrow("captured"))));
        } return rows;
    }
    public void categorize(long id, String category) { ContentValues v = new ContentValues(); v.put("category", category); getWritableDatabase().update("captures", v, "_id=?", new String[]{String.valueOf(id)}); }
    public void resolve(long id, String name, long cents, String category) { ContentValues v = new ContentValues(); v.put("name", name); v.put("cents", cents); v.put("category", category); v.put("pending", 0); getWritableDatabase().update("captures", v, "_id=?", new String[]{String.valueOf(id)}); }
    public void discard(long id) { // Mantém o token para que a mesma notificação não volte a ser importada.
        ContentValues v = new ContentValues(); v.put("pending", 2); v.put("raw", ""); getWritableDatabase().update("captures", v, "_id=?", new String[]{String.valueOf(id)});
    }
    public static final class Record {
        public final long id, cents, time; public final String name, category, raw;
        Record(long id, String name, long cents, String category, String raw, long time) { this.id=id; this.name=name; this.cents=cents; this.category=category; this.raw=raw; this.time=time; }
    }
}
