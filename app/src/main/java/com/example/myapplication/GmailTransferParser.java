package com.example.myapplication;

import java.util.regex.Pattern;

/** Somente saídas do Nubank; recebimentos por e-mail nunca entram. */
public final class GmailTransferParser {
    private static final Pattern BANK = Pattern.compile("(?i)(?<![a-z0-9])nubank(?![a-z0-9])|(?:^|\\n)\\s*nu(?:\\s+pagamentos)?(?:\\s+s\\.?a\\.?)?\\s*(?:$|\\n|[<:·])");
    public static boolean accepts(String title, String body) {
        String text = (title == null ? "" : title) + "\n" + (body == null ? "" : body);
        return BANK.matcher(text).find() && NotificationParser.isSentTransfer(text);
    }
    public static NotificationParser.Result parse(String title, String body) {
        if (!accepts(title, body)) return null;
        NotificationParser.Result result = NotificationParser.parse(title, body);
        return result != null && result.cents < 0 ? result : null;
    }
}
