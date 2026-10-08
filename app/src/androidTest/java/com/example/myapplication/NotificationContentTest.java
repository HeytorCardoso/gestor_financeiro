package com.example.myapplication;

import android.app.Notification;
import android.os.Bundle;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationContentTest {
    @Test public void joinsTitlePreviewAndExpandedBodyWithoutDroppingFields() {
        Bundle extras = new Bundle();
        extras.putCharSequence(Notification.EXTRA_TITLE, "Nu");
        extras.putCharSequence(Notification.EXTRA_TEXT, "Você fez uma transferência");
        extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "Valor: R$ 45,00. Destinatário: Ana");
        NotificationContent c = new NotificationContent(extras);
        assertTrue(GmailTransferParser.accepts(c.title, c.body));
        assertEquals(-4500, GmailTransferParser.parse(c.title, c.body).cents);
    }
    @Test public void preservesMultipleLinesInIndividualNotification() {
        Bundle extras = new Bundle(); extras.putCharSequence(Notification.EXTRA_TITLE, "Nubank");
        extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES, new CharSequence[]{"Pix enviado", "Valor: R$ 12,50"});
        NotificationContent c = new NotificationContent(extras);
        assertEquals(-1250, GmailTransferParser.parse(c.title, c.body).cents);
    }
    @Test public void receivingMailDoesNotGenerateExpense() {
        Bundle extras = new Bundle(); extras.putCharSequence(Notification.EXTRA_TITLE, "Nubank");
        extras.putCharSequence(Notification.EXTRA_TEXT, "Pix recebido"); extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "Você recebeu R$ 10,00 de Ana.");
        NotificationContent c = new NotificationContent(extras);
        assertFalse(GmailTransferParser.accepts(c.title, c.body));
    }
}
