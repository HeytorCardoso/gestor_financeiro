package com.example.myapplication;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class FinanceStoreTest {
    private Context isolated(String label) {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix="finance_test_" + label + "_" + java.util.UUID.randomUUID() + "_";
        return new ContextWrapper(base) {
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler handler) {
                return base.openOrCreateDatabase(prefix+name,mode,factory,handler);
            }
            @Override public java.io.File getDatabasePath(String name) {return base.getDatabasePath(prefix+name);}
        };
    }
    private void addPair(CaptureStore store) {
        store.capture("bank","Você enviou R$ 10,00 para Ana via Pix.",1000);
        String raw="Nubank\nVocê enviou R$ 10,00 para Ana via Pix.";
        store.capture("mail",raw,61000,GmailTransferParser.parse("",raw),"Gmail · Nubank");
    }
    @Test public void duplicateRequiresDecisionAndUnionPreservesEditsAndCanBeUndone() {
        try(CaptureStore store=new CaptureStore(isolated("merge"))) {
            addPair(store);assertEquals(2,store.records(false).size());assertEquals(1,store.possibleDuplicates().size());
            CaptureStore.Duplicate pair=store.possibleDuplicates().get(0);
            long keep=pair.first.id, other=pair.second.id;
            store.edit(keep,"Jantar",-1000,"Lazer","Nota do e-mail",TransactionKind.ACCOUNT_TRANSFER,"Ana");
            store.edit(other,"Transferência para Ana",-1000,"Outros","Nota do Nubank",TransactionKind.ACCOUNT_TRANSFER,"Ana");
            assertTrue(store.merge(keep,other));assertEquals(1,store.records(false).size());
            assertEquals("Nota do e-mail",store.records(false).get(0).notes);
            assertEquals("Nota do Nubank",store.linkedRecords(keep).get(0).notes);
            assertFalse(store.capture("bank","Você enviou R$ 10,00 para Ana via Pix.",1000));
            store.undoMerge(keep);assertEquals(2,store.records(false).size());assertEquals(0,store.possibleDuplicates().size());
        }
    }
    @Test public void differentDecisionSurvivesReopenAndDoesNotRemoveRecords() {
        Context context=isolated("different");
        try(CaptureStore store=new CaptureStore(context)) {
            addPair(store);CaptureStore.Duplicate pair=store.possibleDuplicates().get(0);
            store.markDifferent(pair.first.id,pair.second.id);
        }
        try(CaptureStore store=new CaptureStore(context)) {
            assertEquals(2,store.records(false).size());assertEquals(0,store.possibleDuplicates().size());
        }
    }
    @Test public void deletingUnitedRecordDoesNotResurrectEitherNotification() {
        try(CaptureStore store=new CaptureStore(isolated("delete"))) {
            addPair(store);CaptureStore.Duplicate pair=store.possibleDuplicates().get(0);
            assertTrue(store.merge(pair.first.id,pair.second.id));store.discard(pair.first.id);
            addPair(store);assertEquals(0,store.records(false).size());
        }
    }
    @Test public void totalsCountCreditAndInvoiceSeparatelyAndCanBeCorrected() {
        try(CaptureStore store=new CaptureStore(isolated("totals"))) {
            store.capture("credit","Compra aprovada no crédito\nR$ 100,00 em Loja",1000);
            store.capture("invoice","Pagamento da fatura de R$ 100,00 confirmado.",2000);
            FinancialSummary summary=new FinancialSummary();
            for(CaptureStore.Record r:store.records(false)) summary.add(r.cents,r.kind,r.incomplete);
            assertEquals(20000,summary.expenses);assertEquals(10000,summary.accountOut);assertEquals(-20000,summary.result());
            CaptureStore.Record credit=store.records(false).get(1);
            store.edit(credit.id,credit.name,credit.cents,credit.category,credit.notes,TransactionKind.ACCOUNT_EXPENSE,"");
            assertEquals(TransactionKind.ACCOUNT_EXPENSE,store.records(false).get(1).kind);
        }
    }
    @Test public void chainedUnionsCanRestoreAllOriginalCaptures() {
        try(CaptureStore store=new CaptureStore(isolated("chain"))) {
            addPair(store);
            CaptureStore.Duplicate first=store.possibleDuplicates().get(0);
            long bank=first.second.id; // Banco foi recebido antes do e-mail.
            assertTrue(store.merge(bank,first.first.id));
            String raw="Nubank\nVocê enviou R$ 10,00 para Ana via Pix.";
            store.capture("mail-2",raw,62000,GmailTransferParser.parse("",raw),"Gmail · Nubank");
            long mail=store.records(false).get(0).id;
            assertTrue(store.merge(mail,bank));
            assertEquals(1,store.records(false).size());assertEquals(2,store.linkedRecords(mail).size());
            store.undoMerge(mail);assertEquals(3,store.records(false).size());assertEquals(0,store.linkedRecords(mail).size());
        }
    }
    @Test public void reverseArrivalOrderAlsoDetectsDuplicate() {
        try(CaptureStore store=new CaptureStore(isolated("reverse"))) {
            String raw="Nubank\nVocê enviou R$ 10,00 para Ana via Pix.";
            store.capture("mail",raw,1000,GmailTransferParser.parse("",raw),"Gmail · Nubank");
            store.capture("bank","Você enviou R$ 10,00 para Ana via Pix.",61000);
            assertEquals(1,store.possibleDuplicates().size());
        }
    }
    @Test public void migratesVersionThreeWithoutChangingUserValuesOrNotes() {
        Context context=isolated("migration");
        try(SQLiteDatabase db=context.openOrCreateDatabase("captures.db",Context.MODE_PRIVATE,null,null)) {
            db.execSQL("CREATE TABLE captures (_id INTEGER PRIMARY KEY, token TEXT UNIQUE NOT NULL, raw TEXT NOT NULL, name TEXT NOT NULL, cents INTEGER NOT NULL, category TEXT NOT NULL, captured INTEGER NOT NULL, pending INTEGER NOT NULL, notes TEXT NOT NULL DEFAULT '', edited INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL DEFAULT 'Nubank')");
            db.execSQL("CREATE TABLE changes (revision INTEGER NOT NULL)");db.execSQL("INSERT INTO changes VALUES(0)");
            for(String operation:new String[]{"INSERT","UPDATE","DELETE"}) db.execSQL("CREATE TRIGGER captures_"+operation.toLowerCase(java.util.Locale.ROOT)+" AFTER "+operation+" ON captures BEGIN UPDATE changes SET revision=revision+1; END");
            db.execSQL("INSERT INTO captures(token,raw,name,cents,category,captured,pending,notes,edited) VALUES(?,?,?,?,?,?,?,?,?)",new Object[]{"old","Compra aprovada no crédito de R$ 100,00 em Loja.","Nome personalizado",-12000,"Lazer",1000,0,"Observação antiga",1});
            db.setVersion(3);
        }
        try(CaptureStore store=new CaptureStore(context)) {
            CaptureStore.Record r=store.records(false).get(0);
            assertEquals(-12000,r.cents);assertEquals("Nome personalizado",r.name);assertEquals("Observação antiga",r.notes);
            assertEquals(TransactionKind.CREDIT_PURCHASE,r.kind);assertEquals(4,store.getReadableDatabase().getVersion());
        }
    }
}
