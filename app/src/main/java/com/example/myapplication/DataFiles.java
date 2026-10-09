package com.example.myapplication;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

/** Arquivo lógico versionado; restauração integral e atômica, sem copiar arquivos SQLite abertos. */
public final class DataFiles {
    private static final String[] CAPTURE={"_id","token","raw","name","cents","category","captured","pending","notes","edited","source","kind","counterparty","category_key","category_manual"};
    private static final String[] DECISION={"a","b","status","kept_id"};
    private static final String[] RULE={"identity","category"};
    private static final int MAX_BYTES=20*1024*1024,MAX_ROWS=100000;
    private DataFiles() {}
    public static void csv(CaptureStore store,OutputStream output) throws IOException {
        Writer writer=new OutputStreamWriter(output,StandardCharsets.UTF_8);writer.write('\ufeff');
        CsvExport.row(writer,"ID","Data de captura","Descrição","Valor (R$)","Categoria","Natureza","Destinatário / remetente","Origem","Observações","Status");
        java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",java.util.Locale.ROOT);
        for(CaptureStore.Record r:store.records(false)) CsvExport.row(writer,String.valueOf(r.id),date.format(new java.util.Date(r.time)),r.name,CsvExport.amount(r.cents),r.category,r.kind.label,r.counterparty,r.source,r.notes,r.incomplete ? "Incompleto" : "Lançado");
        writer.flush();
    }
    public static JSONObject backup(CaptureStore store,long now) throws JSONException {
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try {
            JSONObject root=new JSONObject();root.put("format","finna-backup");root.put("version",1);root.put("created",now);
            JSONArray captures=table(db,"captures",CAPTURE);
            // Exclusões em prazo de desfazer continuam excluídas no arquivo, sem levar seus detalhes.
            for(int i=0;i<captures.length();i++) {
                JSONObject r=captures.getJSONObject(i);
                if(r.getInt("pending")==2) {
                    for(String key:new String[]{"raw","notes","counterparty","category_key"})r.put(key,"");
                    r.put("name","Lançamento apagado");r.put("cents",0);r.put("category","Outros");r.put("kind","UNKNOWN");
                }
            }
            root.put("captures",captures);root.put("decisions",table(db,"duplicate_decisions",DECISION));root.put("rules",table(db,"category_rules",RULE));
            db.setTransactionSuccessful();return root;
        } finally {db.endTransaction();}
    }
    public static void writeBackup(CaptureStore store,OutputStream output,long now) throws IOException,JSONException {
        JSONObject root=backup(store,now);validate(root);
        byte[] bytes=root.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new IOException("Backup maior que 20 MB");
        output.write(bytes);output.flush();
    }
    private static JSONArray table(SQLiteDatabase db,String table,String[] columns) throws JSONException {
        JSONArray rows=new JSONArray();
        try(Cursor c=db.query(table,columns,null,null,null,null,null)) {
            while(c.moveToNext()) {
                JSONObject row=new JSONObject();
                for(int i=0;i<columns.length;i++)row.put(columns[i],c.getType(i)==Cursor.FIELD_TYPE_INTEGER ? c.getLong(i) : c.getString(i));
                rows.put(row);
            }
        }return rows;
    }
    public static JSONObject read(InputStream input) throws IOException,JSONException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=input.read(buffer))!=-1) {if(bytes.size()+n>MAX_BYTES)throw new IOException("Backup maior que 20 MB");bytes.write(buffer,0,n);}
        JSONObject root=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));validate(root);return root;
    }
    private static long number(JSONObject row,String key) throws JSONException {
        Object v=row.get(key);if(!(v instanceof Integer || v instanceof Long))throw new JSONException("Número inválido: "+key);return ((Number)v).longValue();
    }
    private static String string(JSONObject row,String key) throws JSONException {
        Object v=row.get(key);if(!(v instanceof String))throw new JSONException("Texto inválido: "+key);return (String)v;
    }
    private static void flag(JSONObject row,String key,int max) throws JSONException {long n=number(row,key);if(n<0||n>max)throw new JSONException("Estado inválido: "+key);}
    public static void validate(JSONObject root) throws JSONException {
        if(!"finna-backup".equals(string(root,"format")) || number(root,"version")!=1)throw new JSONException("Formato de backup incompatível");
        JSONArray rows=root.getJSONArray("captures"),decisions=root.getJSONArray("decisions"),rules=root.getJSONArray("rules");
        if(rows.length()>MAX_ROWS||decisions.length()>MAX_ROWS||rules.length()>MAX_ROWS)throw new JSONException("Backup excede o limite de registros");
        HashSet<Long> ids=new HashSet<>(),active=new HashSet<>(),hidden=new HashSet<>();HashSet<String> tokens=new HashSet<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject r=rows.getJSONObject(i);long id=number(r,"_id"),cents=number(r,"cents");
            if(id<=0||!ids.add(id)||!tokens.add(string(r,"token"))||string(r,"token").isEmpty())throw new JSONException("Identidade repetida ou inválida");
            if(cents==Long.MIN_VALUE || Math.abs(cents)>100000000000000L || number(r,"captured")<0)throw new JSONException("Valor ou data inválido");
            for(String key:new String[]{"raw","name","notes","source","counterparty","category_key"})string(r,key);
            if(!CategoryRules.valid(string(r,"category")))throw new JSONException("Categoria inválida");
            try {TransactionKind.valueOf(string(r,"kind"));}catch(IllegalArgumentException e){throw new JSONException("Natureza inválida");}
            flag(r,"pending",3);flag(r,"edited",1);flag(r,"category_manual",1);
            int state=r.getInt("pending");if(state==0||state==1)active.add(id);if(state==3)hidden.add(id);
            if(state==0 && cents==0 || state==1 && cents!=0)throw new JSONException("Estado e valor inconsistentes");
        }
        HashSet<String> pairs=new HashSet<>();HashSet<Long> linked=new HashSet<>();
        for(int i=0;i<decisions.length();i++) {
            JSONObject r=decisions.getJSONObject(i);long a=number(r,"a"),b=number(r,"b"),keep=number(r,"kept_id"),status=number(r,"status");
            if(a>=b||!ids.contains(a)||!ids.contains(b)||!pairs.add(a+":"+b)||(status!=1&&status!=2))throw new JSONException("Vínculo inválido");
            if(status==1) {
                if(!ids.contains(keep))throw new JSONException("Fonte principal ausente");
                if(active.contains(keep)) {if(hidden.contains(a))linked.add(a);if(hidden.contains(b))linked.add(b);}
            }
        }
        if(!linked.containsAll(hidden))throw new JSONException("Fonte unida sem lançamento principal");
        HashSet<String> identities=new HashSet<>();
        for(int i=0;i<rules.length();i++) {JSONObject r=rules.getJSONObject(i);String key=string(r,"identity");if(key.isEmpty()||!identities.add(key)||!CategoryRules.valid(string(r,"category")))throw new JSONException("Regra de categoria inválida");}
    }
    public static void restore(CaptureStore store,JSONObject root) throws JSONException {
        validate(root);SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try {
            for(String table:new String[]{"deletion_items","deletion_batches","duplicate_decisions","category_rules","captures"})db.delete(table,null,null);
            insert(db,"captures",root.getJSONArray("captures"),CAPTURE);
            insert(db,"duplicate_decisions",root.getJSONArray("decisions"),DECISION);
            insert(db,"category_rules",root.getJSONArray("rules"),RULE);
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    private static void insert(SQLiteDatabase db,String table,JSONArray rows,String[] columns) throws JSONException {
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);ContentValues values=new ContentValues();
            for(String key:columns) {Object value=row.get(key);if(value instanceof Number)values.put(key,((Number)value).longValue());else values.put(key,(String)value);}
            db.insertOrThrow(table,null,values);
        }
    }
}
