package com.example.myapplication;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureStoreTest {
    @Test public void persistsDeduplicatesAndPromotesPending() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Nome de banco isolado por ContextWrapper, sem apagar dados do usuário.
        Context isolated = new android.content.ContextWrapper(context) {
            @Override public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String name, int mode, android.database.sqlite.SQLiteDatabase.CursorFactory factory, android.database.DatabaseErrorHandler handler) {
                return super.openOrCreateDatabase("test_" + name, mode, factory, handler);
            }
            @Override public java.io.File getDatabasePath(String name) { return super.getDatabasePath("test_" + name); }
        };
        try (CaptureStore store = new CaptureStore(isolated)) {
            store.getWritableDatabase().delete("captures", null, null);
            assertTrue(store.capture("event-1", "Conteúdo oculto", 1));
            assertEquals(1, store.records(true).size());
            assertEquals(1, store.records(false).size());
            assertTrue(store.records(false).get(0).incomplete);
            assertTrue(store.capture("event-1", "Você recebeu R$ 10,00 de Ana via Pix.", 1));
            assertEquals(0, store.records(true).size());
            assertEquals(1000, store.records(false).get(0).cents);
            assertFalse(store.capture("event-1", "Você recebeu R$ 10,00 de Ana via Pix.", 1));
            assertTrue(store.capture("event-2", "Você recebeu R$ 10,00 de Ana via Pix.", 2));
            assertEquals(2, store.records(false).size());
            CaptureStore.Record r = store.records(false).get(0);
            store.edit(r.id, "Pagamento personalizado", -2350, "Lazer", "Jantar com amigos");
            assertFalse(store.capture("event-2", "Você recebeu R$ 10,00 de Ana via Pix.", 2));
            CaptureStore.Record edited = store.records(false).get(0);
            assertEquals(-2350, edited.cents);
            assertEquals("Jantar com amigos", edited.notes);
            assertEquals("Lazer", edited.category);
            assertEquals("Pagamento personalizado", edited.name);
            store.capture("event-3", "Conteúdo indisponível", 3);
            CaptureStore.Record incomplete = store.records(false).get(0);
            store.edit(incomplete.id, "Detalhes salvos", 0, "Casa", "Conferir depois");
            assertFalse(store.capture("event-3", "Você recebeu R$ 10,00 de Ana via Pix.", 3));
            assertEquals("Conferir depois", store.records(false).get(0).notes);
            assertTrue(store.records(false).get(0).incomplete);
        }
        try (CaptureStore store = new CaptureStore(isolated)) {
            store.capture("event-reprocess", "Compra aprovada\nR$ 15,00 em Padaria", 4, null, "Nubank");
            store.reprocessIncomplete();
            assertEquals(-1500, store.records(false).get(0).cents);
            long deleted = store.records(false).get(0).id;
            store.discard(deleted);
            assertFalse(store.capture("event-reprocess", "Compra aprovada\nR$ 15,00 em Padaria", 4));
            assertEquals(3, store.records(false).size());
        }
        try (CaptureStore reopened = new CaptureStore(isolated)) { assertEquals(3, reopened.records(false).size()); assertEquals("Conferir depois", reopened.records(false).get(0).notes); }
    }
}
