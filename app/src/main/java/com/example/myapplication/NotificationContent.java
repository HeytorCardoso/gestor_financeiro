package com.example.myapplication;

import android.app.Notification;
import android.os.Bundle;
import java.util.LinkedHashSet;

/** Preserva campos complementares sem descartar o título ou repetir textos iguais. */
public final class NotificationContent {
    public final String title, body;
    public final String[] lines;
    public NotificationContent(Bundle extras) {
        LinkedHashSet<String> titles = new LinkedHashSet<>(), bodies = new LinkedHashSet<>();
        add(titles, extras.getCharSequence(Notification.EXTRA_TITLE));
        add(titles, extras.getCharSequence(Notification.EXTRA_TITLE_BIG));
        add(bodies, extras.getCharSequence(Notification.EXTRA_TEXT));
        add(bodies, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        CharSequence[] array = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        java.util.ArrayList<String> individual = new java.util.ArrayList<>();
        if (array != null) for (CharSequence line : array) if (line != null && !line.toString().trim().isEmpty()) {
            add(bodies, line); individual.add(line.toString());
        }
        title = android.text.TextUtils.join("\n", titles); body = android.text.TextUtils.join("\n", bodies);
        lines = individual.toArray(new String[0]);
    }
    private static void add(LinkedHashSet<String> target, CharSequence value) {
        if (value != null && !value.toString().trim().isEmpty()) target.add(value.toString().trim());
    }
    public String raw() { return title + "\n" + body; }
}
