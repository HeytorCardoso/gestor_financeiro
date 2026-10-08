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
            assertTrue(store.capture("event-1", "Você recebeu R$ 10,00 de Ana via Pix.", 1));
            assertEquals(0, store.records(true).size());
            assertEquals(1000, store.records(false).get(0).cents);
            assertFalse(store.capture("event-1", "Você recebeu R$ 10,00 de Ana via Pix.", 1));
            assertTrue(store.capture("event-2", "Você recebeu R$ 10,00 de Ana via Pix.", 2));
            assertEquals(2, store.records(false).size());
        }
        try (CaptureStore reopened = new CaptureStore(isolated)) { assertEquals(2, reopened.records(false).size()); }
    }
}
