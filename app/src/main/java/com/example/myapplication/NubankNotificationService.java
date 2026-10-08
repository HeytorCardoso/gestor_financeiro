package com.example.myapplication;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NubankNotificationService extends NotificationListenerService {
    public static volatile boolean connected;
    public static final String NUBANK_PACKAGE = "com.nu.production";
    public static final String GMAIL_PACKAGE = "com.google.android.gm";
    @Override public void onListenerConnected() { connected = true; }
    @Override public void onListenerDisconnected() { connected = false; }
    @Override public void onDestroy() { connected = false; super.onDestroy(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        boolean gmail = GMAIL_PACKAGE.equals(sbn.getPackageName());
        if (!gmail && !NUBANK_PACKAGE.equals(sbn.getPackageName())) return;
        if (getSharedPreferences("capture", MODE_PRIVATE).getBoolean("paused", false)) return;
        Notification notification = sbn.getNotification();
        if (notification.extras == null) return;
        NotificationContent content = new NotificationContent(notification.extras);
        boolean summary = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if (summary) {
            if (gmail) gmailStatus("Resumo agrupado recebido. Aguardando as notificações individuais dos e-mails.");
            return; // Um resumo pode conter saídas e entradas diferentes; nunca somar como uma transação.
        }
        long eventTime = notification.when > 0 ? notification.when : sbn.getPostTime();
        String token = sbn.getKey() + ":" + eventTime;
        try (CaptureStore store = new CaptureStore(this)) {
            if (gmail) {
                if (!GmailTransferParser.accepts(content.title, content.body)) {
                    gmailStatus("Notificação recebida, mas sem identificação de transferência enviada do Nubank. Recebimentos são ignorados.");
                    return;
                }
                NotificationParser.Result parsed = GmailTransferParser.parse(content.title, content.body);
                store.capture(token, content.raw(), sbn.getPostTime(), parsed, "Gmail · Nubank");
                gmailStatus(parsed == null ? "Envio identificado. O conteúdo visível não permitiu extrair um valor único." : "Transferência enviada reconhecida e lançada automaticamente.");
            } else {
                store.capture(token, content.raw(), sbn.getPostTime(), NotificationParser.parse(content.title, content.body), "Nubank");
            }
        }
    }
    private void gmailStatus(String status) {
        getSharedPreferences("capture", MODE_PRIVATE).edit().putString("gmailStatus", status).putLong("gmailSeenAt", System.currentTimeMillis()).apply();
    }
}
