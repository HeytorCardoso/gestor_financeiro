package com.example.myapplication;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class DataFilesStoreTest {
    private Context isolated() {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();String prefix="files_test_"+java.util.UUID.randomUUID()+"_";
        return new ContextWrapper(base) {
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler handler) {return base.openOrCreateDatabase(prefix+name,mode,factory,handler);}
            @Override public java.io.File getDatabasePath(String name) {return base.getDatabasePath(prefix+name);}
        };
    }
    private JSONObject serialized(CaptureStore store) throws Exception {
        byte[] bytes=DataFiles.backup(store,4000).toString().getBytes(StandardCharsets.UTF_8);
        return DataFiles.read(new ByteArrayInputStream(bytes));
    }
    @Test public void roundTripPreservesEditsRulesMergedSourcesAndTombstones() throws Exception {
        try(CaptureStore source=new CaptureStore(isolated());CaptureStore target=new CaptureStore(isolated())) {
            String raw="Você enviou R$ 10,00 para Ana via Pix.";
            source.capture("bank",raw,1000);long keep=source.records(false).get(0).id;
            source.editDetails(keep,"Casa","Detalhes pessoais",true);
            source.capture("mail",raw,2000,NotificationParser.parse(raw),"Gmail · Nubank");long other=source.records(false).get(0).id;
            assertTrue(source.merge(keep,other));
            source.capture("deleted","Compra aprovada de R$ 20,00 em Loja.",3000);source.discard(source.records(false).get(0).id);
            target.capture("old","Você recebeu R$ 99,00 de Outra pessoa via Pix.",5000);
            DataFiles.restore(target,serialized(source));
            assertEquals(1,target.records(false).size());CaptureStore.Record restored=target.records(false).get(0);
            assertEquals("Casa",restored.category);assertEquals("Detalhes pessoais",restored.notes);assertEquals(raw,restored.raw);
            assertEquals(1,target.linkedRecords(keep).size());assertTrue(target.possibleDuplicates().isEmpty());
            assertFalse(target.capture("deleted","Compra aprovada de R$ 21,00 em Loja.",6000));
            target.capture("new","Você enviou R$ 30,00 para Ana via Pix.",7000);assertEquals("Casa",target.records(false).get(0).category);
            target.undoMerge(keep);assertEquals(3,target.records(false).size());
        }
    }
    @Test public void invalidBackupDoesNotEraseCurrentData() throws Exception {
        try(CaptureStore store=new CaptureStore(isolated())) {
            store.capture("one","Compra aprovada de R$ 10,00 em Loja.",1000);
            JSONObject invalid=serialized(store);invalid.getJSONArray("captures").getJSONObject(0).put("cents","1000");
            try {DataFiles.restore(store,invalid);fail("Should reject numeric string");}catch(JSONException expected) {}
            assertEquals(1,store.records(false).size());assertEquals(-1000,store.records(false).get(0).cents);
            invalid=serialized(store);invalid.put("version",999);
            try {DataFiles.restore(store,invalid);fail("Should reject future version");}catch(JSONException expected) {}
            assertEquals(1,store.records(false).size());
        }
    }
    @Test public void insertionFailureRollsBackEntireReplacement() throws Exception {
        try(CaptureStore source=new CaptureStore(isolated());CaptureStore target=new CaptureStore(isolated())) {
            source.capture("new","Compra aprovada de R$ 10,00 em Loja.",1000);
            target.capture("old","Você recebeu R$ 50,00 de Ana via Pix.",2000);
            target.getWritableDatabase().execSQL("CREATE TRIGGER reject_test BEFORE INSERT ON captures WHEN NEW.token='new' BEGIN SELECT RAISE(ABORT,'test failure'); END");
            try {DataFiles.restore(target,serialized(source));fail("Should fail insertion");}catch(android.database.sqlite.SQLiteException expected) {}
            assertEquals(1,target.records(false).size());assertEquals(5000,target.records(false).get(0).cents);
        }
    }
    @Test public void pendingDeletionIsExportedAsScrubbedTombstone() throws Exception {
        try(CaptureStore source=new CaptureStore(isolated());CaptureStore target=new CaptureStore(isolated())) {
            source.capture("one","Compra aprovada de R$ 10,00 em Loja.",1000);source.deleteWithUndo(source.records(false).get(0).id,2000);
            JSONObject backup=serialized(source);JSONObject row=backup.getJSONArray("captures").getJSONObject(0);
            assertEquals("",row.getString("raw"));assertEquals(0,row.getLong("cents"));
            assertNotNull(source.latestDeletion(2001)); // Exporting must not cancel undo in the source.
            DataFiles.restore(target,backup);assertTrue(target.records(false).isEmpty());assertNull(target.latestDeletion(2001));
            assertFalse(target.capture("one","Compra aprovada de R$ 20,00 em Loja.",3000));
        }
    }
    @Test public void rejectsDuplicateIdentityOrOrphanMergedSource() throws Exception {
        try(CaptureStore store=new CaptureStore(isolated())) {
            store.capture("one","Compra aprovada de R$ 10,00 em Loja.",1000);
            JSONObject backup=serialized(store);backup.getJSONArray("captures").put(new JSONObject(backup.getJSONArray("captures").getJSONObject(0).toString()));
            try {DataFiles.restore(store,backup);fail("Should reject duplicates");}catch(JSONException expected) {}
            backup=serialized(store);backup.getJSONArray("captures").getJSONObject(0).put("pending",3);
            try {DataFiles.restore(store,backup);fail("Should reject orphan source");}catch(JSONException expected) {}
            assertEquals(1,store.records(false).size());
        }
    }
    @Test public void csvContainsOnlyActiveRowsWithExactSignedAmountsAndEscaping() throws Exception {
        try(CaptureStore store=new CaptureStore(isolated())) {
            store.capture("one","Compra aprovada de R$ 10,01 em Loja.",1000);CaptureStore.Record r=store.records(false).get(0);
            store.edit(r.id,"Loja; \"Centro\"",r.cents,"Casa","=1+1");
            store.capture("two","Você recebeu R$ 20,00 de Ana via Pix.",2000);store.discard(store.records(false).get(0).id);
            ByteArrayOutputStream output=new ByteArrayOutputStream();DataFiles.csv(store,output);
            String csv=new String(output.toByteArray(),StandardCharsets.UTF_8);
            assertTrue(csv.startsWith("\ufeff"));assertTrue(csv.contains("\"-10,01\""));assertTrue(csv.contains("\"Loja; \"\"Centro\"\"\""));
            assertTrue(csv.contains("\"'=1+1\""));assertFalse(csv.contains("Lançamento apagado"));
        }
    }
    @Test public void differentDecisionsAndIncompleteRecordsRemainAfterRestore() throws Exception {
        try(CaptureStore source=new CaptureStore(isolated());CaptureStore target=new CaptureStore(isolated())) {
            String raw="Você enviou R$ 10,00 para Ana via Pix.";
            source.capture("bank",raw,1000);long first=source.records(false).get(0).id;
            source.capture("mail",raw,2000,NotificationParser.parse(raw),"Gmail · Nubank");source.markDifferent(first,source.records(false).get(0).id);
            source.capture("unknown","Texto sem valor",3000);
            DataFiles.restore(target,serialized(source));assertEquals(3,target.records(false).size());assertEquals(1,target.records(true).size());assertTrue(target.possibleDuplicates().isEmpty());
        }
    }
}
