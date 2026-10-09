package com.example.myapplication;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class CategoryLearningStoreTest {
    private Context isolated() {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();String prefix="category_test_"+java.util.UUID.randomUUID()+"_";
        return new ContextWrapper(base) {
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler handler) {return base.openOrCreateDatabase(prefix+name,mode,factory,handler);}
            @Override public java.io.File getDatabasePath(String name) {return base.getDatabasePath(prefix+name);}
        };
    }
    private void purchase(CaptureStore store,String token,String merchant,long time) {store.capture(token,"Compra aprovada de R$ 10,00 em "+merchant+".",time);}
    private void categorize(CaptureStore store,CaptureStore.Record r,String category,boolean remember) {
        store.edit(r.id,"Descrição editada",r.cents,category,"Observação",r.kind,r.counterparty,remember);
    }
    @Test public void learnsOriginalMerchantAndPersistsAcrossReopen() {
        Context context=isolated();
        try(CaptureStore store=new CaptureStore(context)) {
            purchase(store,"one","Café Central",1000);categorize(store,store.records(false).get(0),"Alimentação",true);
        }
        try(CaptureStore store=new CaptureStore(context)) {
            purchase(store,"two","CAFE CENTRAL",2000);
            CaptureStore.Record automatic=store.records(false).get(0);
            assertEquals("Alimentação",automatic.category);assertFalse(automatic.categoryManual);
            purchase(store,"three","Outra Loja",3000);assertEquals("Outros",store.records(false).get(0).category);
        }
    }
    @Test public void localCorrectionDoesNotTeachAndIsNotOverwritten() {
        try(CaptureStore store=new CaptureStore(isolated())) {
            purchase(store,"one","Loja",1000);CaptureStore.Record first=store.records(false).get(0);
            categorize(store,first,"Casa",false);purchase(store,"two","Loja",2000);
            assertEquals("Outros",store.records(false).get(0).category);
            assertFalse(store.capture("one","Compra aprovada de R$ 12,00 em Loja.",1000));
            assertEquals("Casa",store.records(false).get(1).category);assertTrue(store.records(false).get(1).categoryManual);
        }
    }
    @Test public void latestExplicitCorrectionAffectsNewCapturesOnly() {
        try(CaptureStore store=new CaptureStore(isolated())) {
            purchase(store,"one","Loja",1000);categorize(store,store.records(false).get(0),"Casa",true);
            purchase(store,"two","Loja",2000);categorize(store,store.records(false).get(0),"Lazer",true);
            purchase(store,"three","Loja",3000);
            assertEquals("Lazer",store.records(false).get(0).category);
            assertEquals("Casa",store.records(false).get(2).category);
        }
    }
    @Test public void learningTransfersWorksAcrossGmailAndBankWithoutAffectingIncome() {
        try(CaptureStore store=new CaptureStore(isolated())) {
            store.capture("bank","Você enviou R$ 10,00 para Ana via Pix.",1000);
            categorize(store,store.records(false).get(0),"Lazer",true);
            String raw="Nubank\nVocê enviou R$ 25,00 para Ana via Pix.";
            store.capture("mail",raw,2000,GmailTransferParser.parse("",raw),"Gmail · Nubank");
            assertEquals("Lazer",store.records(false).get(0).category);
            store.capture("income","Você recebeu R$ 25,00 de Ana via Pix.",3000);
            assertEquals("Outros",store.records(false).get(0).category);
        }
    }
    @Test public void genericDescriptionsDoNotTeachAllPurchases() {
        try(CaptureStore store=new CaptureStore(isolated())) {
            store.capture("one","Compra aprovada de R$ 10,00.",1000);
            categorize(store,store.records(false).get(0),"Casa",true);
            store.capture("two","Compra aprovada de R$ 20,00.",2000);
            assertEquals("Outros",store.records(false).get(0).category);
        }
    }
    @Test public void reprocessingIncompleteCaptureAppliesLearnedCategory() {
        try(CaptureStore store=new CaptureStore(isolated())) {
            purchase(store,"one","Loja",1000);categorize(store,store.records(false).get(0),"Casa",true);
            store.capture("two","Compra aprovada de R$ 20,00 em Loja.",2000,null,"Nubank");
            store.reprocessIncomplete();assertEquals("Casa",store.records(false).get(0).category);
        }
    }
    @Test public void editingOnlyNotesDoesNotOverwriteLearnedRule() {
        try(CaptureStore store=new CaptureStore(isolated())) {
            purchase(store,"one","Loja",1000);categorize(store,store.records(false).get(0),"Casa",true);
            purchase(store,"two","Loja",2000);categorize(store,store.records(false).get(0),"Lazer",false);
            CaptureStore.Record first=store.records(false).get(0);
            store.edit(first.id,first.name,first.cents,first.category,"Nova observação",first.kind,first.counterparty,true);
            purchase(store,"three","Loja",3000);assertEquals("Casa",store.records(false).get(0).category);
        }
    }
}
