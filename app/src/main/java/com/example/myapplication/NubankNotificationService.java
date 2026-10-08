package com.example.myapplication;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NubankNotificationService extends NotificationListenerService {
    public static volatile boolean connected;
    public static final String NUBANK_PACKAGE = "com.nu.production";
    @Override public void onListenerConnected() { connected = true; }
    @Override public void onListenerDisconnected() { connected = false; }
    @Override public void onDestroy() { connected = false; super.onDestroy(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !NUBANK_PACKAGE.equals(sbn.getPackageName())) return;
        if (getSharedPreferences("capture", MODE_PRIVATE).getBoolean("paused", false)) return;
        Notification notification = sbn.getNotification();
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle extras = notification.extras;
        if (extras == null) return;
        CharSequence body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (body == null || body.toString().trim().isEmpty()) body = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (body == null || body.toString().trim().isEmpty()) {
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) body = android.text.TextUtils.join("\n", lines);
        }
        if (body == null || body.toString().trim().isEmpty()) body = extras.getCharSequence(Notification.EXTRA_TITLE);
        String raw = body == null ? "Conteúdo indisponível nesta notificação" : body.toString();
        long eventTime = notification.when > 0 ? notification.when : sbn.getPostTime();
        String token = sbn.getKey() + ":" + eventTime;
        try (CaptureStore store = new CaptureStore(this)) {
            store.capture(token, raw, sbn.getPostTime());
        }
    }
}
