package com.example.myapplication;

import java.util.regex.Pattern;

/** Somente saídas do Nubank; recebimentos por e-mail nunca entram. */
public final class GmailTransferParser {
    private static final Pattern BANK = Pattern.compile("(?i)(?<![a-z0-9])nubank(?![a-z0-9])|(?:^|\\n)\\s*nu(?:\\s+pagamentos)?(?:\\s+s\\.?a\\.?)?\\s*(?:$|\\n|[<:·])");
    public static boolean accepts(String title, String body) {
        String text = (title == null ? "" : title) + "\n" + (body == null ? "" : body);
        // Alguns avisos do Gmail expõem o assunto como título, sem o nome do remetente.
        // Nesse caso exigimos a estrutura completa do comprovante fornecido pelo usuário.
        String folded = NotificationParser.fold(text);
        boolean suppliedTemplate = folded.contains("transferencia realizada com sucesso")
                && Pattern.compile("\\b(?:a\\s+)?transferencia\\s+para\\s+[^\\n]+?\\s+foi\\s+realizada\\s+com\\s+sucesso\\b").matcher(folded).find()
                && Pattern.compile("\\bvalor\\s+enviado\\s*:").matcher(folded).find();
        return (BANK.matcher(text).find() || suppliedTemplate) && NotificationParser.isSentTransfer(text);
    }
    public static NotificationParser.Result parse(String title, String body) {
        if (!accepts(title, body)) return null;
        NotificationParser.Result result = NotificationParser.parse(title, body);
        return result != null && result.cents < 0 ? result : null;
    }
}
