package com.example.myapplication;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuickEditDeletionStoreTest {
    private Context isolated() {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();String prefix="undo_test_"+java.util.UUID.randomUUID()+"_";
        return new ContextWrapper(base) {
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler handler) {return base.openOrCreateDatabase(prefix+name,mode,factory,handler);}
            @Override public java.io.File getDatabasePath(String name) {return base.getDatabasePath(prefix+name);}
        };
    }
    private void purchase(CaptureStore store) {store.capture("one","Compra aprovada de R$ 10,00 em Loja.",1000);}
    @Test public void upgradesVersionFiveWithoutChangingExistingEntries() {
        Context context=isolated();
        try(CaptureStore store=new CaptureStore(context)) {
            purchase(store);store.editDetails(store.records(false).get(0).id,"Casa","Preservar",true);
            SQLiteDatabase db=store.getWritableDatabase();
            db.execSQL("DROP TABLE deletion_items");db.execSQL("DROP TABLE deletion_batches");db.setVersion(5);
        }
        try(CaptureStore store=new CaptureStore(context)) {
            CaptureStore.Record r=store.records(false).get(0);
            assertEquals(6,store.getReadableDatabase().getVersion());assertEquals(-1000,r.cents);
            assertEquals("Casa",r.category);assertEquals("Preservar",r.notes);
            assertTrue(store.deleteWithUndo(r.id,2000));assertTrue(store.undoDeletion(store.latestDeletion(2000).id,2001));
        }
    }
    @Test public void quickEditPreservesValuesAndLearnsCategory() {
        try(CaptureStore store=new CaptureStore(isolated())) {
            purchase(store);CaptureStore.Record r=store.records(false).get(0);
            assertTrue(store.editDetails(r.id,"Casa","Detalhes",true));
            CaptureStore.Record changed=store.records(false).get(0);
            assertEquals(r.cents,changed.cents);assertEquals(r.name,changed.name);assertEquals(r.kind,changed.kind);assertEquals(r.raw,changed.raw);assertEquals("Detalhes",changed.notes);
            store.capture("two","Compra aprovada de R$ 20,00 em Loja.",2000);assertEquals("Casa",store.records(false).get(0).category);
        }
    }
    @Test public void undoSurvivesReopenAndPreservesEditsAndBlocksNotifications() {
        Context context=isolated();long batch,id;
        try(CaptureStore store=new CaptureStore(context)) {
            purchase(store);id=store.records(false).get(0).id;store.editDetails(id,"Lazer","Nota",false);
            assertTrue(store.deleteWithUndo(id,2000));batch=store.latestDeletion(2000).id;
            assertTrue(store.records(false).isEmpty());assertFalse(store.capture("one","Compra aprovada de R$ 20,00 em Loja.",3000));
            assertFalse(store.editDetails(id,"Casa","Outra",false));
        }
        try(CaptureStore store=new CaptureStore(context)) {
            assertTrue(store.undoDeletion(batch,3000));CaptureStore.Record r=store.records(false).get(0);
            assertEquals(id,r.id);assertEquals(-1000,r.cents);assertEquals("Lazer",r.category);assertEquals("Nota",r.notes);
            assertFalse(store.undoDeletion(batch,3001));
        }
    }
    @Test public void expirationPermanentlyClearsDataWithoutResurrection() {
        try(CaptureStore store=new CaptureStore(isolated())) {
            purchase(store);long id=store.records(false).get(0).id;store.deleteWithUndo(id,2000);long batch=store.latestDeletion(2000).id;
            long end=2000+CaptureStore.UNDO_WINDOW_MS;assertFalse(store.undoDeletion(batch,end));store.purgeExpiredDeletions(end);
            assertNull(store.latestDeletion(end));assertTrue(store.records(false).isEmpty());assertFalse(store.capture("one","Compra aprovada de R$ 20,00 em Loja.",end));
            try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT raw,notes,cents FROM captures WHERE _id=?",new String[]{String.valueOf(id)})) {
                assertTrue(c.moveToFirst());assertEquals("",c.getString(0));assertEquals("",c.getString(1));assertEquals(0,c.getLong(2));
            }
        }
    }
    @Test public void undoRestoresMergedSourcesWithoutCountingBoth() {
        try(CaptureStore store=new CaptureStore(isolated())) {
            String raw="Você enviou R$ 10,00 para Ana via Pix.";
            store.capture("bank",raw,1000);long keep=store.records(false).get(0).id;
            store.capture("mail",raw,2000,NotificationParser.parse(raw),"Gmail · Nubank");long other=store.records(false).get(0).id;
            assertTrue(store.merge(keep,other));assertTrue(store.deleteWithUndo(keep,3000));
            assertTrue(store.undoDeletion(store.latestDeletion(3000).id,3001));
            assertEquals(1,store.records(false).size());assertEquals(1,store.linkedRecords(keep).size());
            store.undoMerge(keep);assertEquals(2,store.records(false).size());
        }
    }
    @Test public void undoRestoresIncompleteStatusAndMultipleIndependentDeletions() {
        try(CaptureStore store=new CaptureStore(isolated())) {
            purchase(store);long first=store.records(false).get(0).id;
            store.capture("unknown","Texto sem valor",2000);long second=store.records(false).get(0).id;
            store.deleteWithUndo(first,3000);long firstBatch=store.latestDeletion(3000).id;
            store.deleteWithUndo(second,4000);long secondBatch=store.latestDeletion(4000).id;
            assertTrue(store.undoDeletion(secondBatch,4001));assertTrue(store.records(false).get(0).incomplete);
            assertEquals(firstBatch,store.latestDeletion(4001).id);
            assertTrue(store.undoDeletion(firstBatch,4002));assertEquals(2,store.records(false).size());
        }
    }
}
