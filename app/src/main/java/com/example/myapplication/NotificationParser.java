package com.example.myapplication;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extrai os dados por significado, sem exigir uma frase ou horário exatos. */
public final class NotificationParser {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern AMOUNT = Pattern.compile("R\\$\\s*((?:\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:,\\d{1,2})?)(?![\\d,])", FLAGS);
    private static final Pattern FAILED = Pattern.compile("\\b(?:recusad[oa]|cancelad[oa]|agendad[oa]|pendente|falhou|estornad[oa])\\b|\\bnao\\s+(?:foi\\s+)?(?:realizad[oa]|concluid[oa]|aprovad[oa])\\b");
    private static final Pattern IN = Pattern.compile("\\brecebeu\\b|\\brecebid[oa]\\b|\\brecebimento\\b|\\bpix\\s+na\\s+sua\\s+conta\\b");
    private static final Pattern OUT = Pattern.compile("\\bvoce\\s+(?:enviou|transferiu)\\b|\\b(?:pix|transferencia)\\s+(?:foi\\s+)?(?:enviad[oa]|realizad[oa]|concluid[oa]|efetuad[oa]|feit[oa])\\b|\\bvoce\\s+(?:fez|realizou)\\s+(?:um(?:a)?\\s+)?(?:pix|transferencia)\\b|\\bcomprovante\\s+(?:de\\s+)?(?:pix|transferencia)\\b");
    private static final Pattern PURCHASE = Pattern.compile("\\bcompra\\b[\\s\\S]*\\baprovad[oa]\\b|\\bcompra\\s+(?:de|realizada|efetuada)\\b|\\bcompra\\s+(?:no|com\\s+(?:o|seu))\\s+cartao\\b|\\bvoce\\s+(?:acabou\\s+de\\s+)?(?:fez|fazer|realizou)\\s+uma\\s+compra\\b");
    private static final Pattern BOLETO = Pattern.compile("\\bboleto\\b[\\s\\S]*\\b(?:sucesso|realizado|pago|efetuado)\\b|\\b(?:pagamento\\s+(?:de\\s+)?boleto|boleto\\s+pago)\\b");

    static String fold(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace('\u00a0', ' ');
    }
    public static boolean isSentTransfer(String text) {
        String folded = fold(text);
        return (OUT.matcher(folded).find() || isSuccessfulTransferToRecipient(folded)) && !IN.matcher(folded).find() && !FAILED.matcher(folded).find();
    }
    private static boolean isSuccessfulTransferToRecipient(String folded) {
        return Pattern.compile("\\btransferencia\\s+para\\s+[^\\n]+?\\s+foi\\s+realizada\\s+com\\s+sucesso\\b").matcher(folded).find();
    }
    public static Result parse(String text) { return parse("", text); }
    public static Result parse(String title, String body) {
        String text = ((title == null ? "" : title) + "\n" + (body == null ? "" : body)).replace('\u00a0', ' ').trim();
        String folded = fold(text);
        if (FAILED.matcher(folded).find()) return null;
        boolean incoming = IN.matcher(folded).find();
        boolean outgoing = OUT.matcher(folded).find() || isSuccessfulTransferToRecipient(folded);
        boolean purchase = PURCHASE.matcher(folded).find();
        boolean boleto = BOLETO.matcher(folded).find();
        if (incoming && (outgoing || purchase || boleto)) return null;
        if (!incoming && !outgoing && !purchase && !boleto) return null;
        Long cents = amount(text);
        if (cents == null) return null;
        String name;
        if (purchase) name = party(text, true, false);
        else if (boleto) name = "Pagamento de boleto";
        else name = party(text, false, incoming);
        if (name == null) name = purchase ? "Compra no cartão" : incoming ? "Pix recebido" : "Transferência enviada";
        return new Result(name, incoming ? cents : -cents);
    }
    private static Long amount(String text) {
        Matcher m = AMOUNT.matcher(text); Long found = null;
        while (m.find()) {
            // Saldo/limite podem estar na mesma notificação, mas não são o valor lançado.
            String before = fold(text.substring(Math.max(0, m.start() - 55), m.start()));
            if (before.matches("(?s).*\\b(?:saldo|limite|disponivel|fatura atual|taxa)\\s*(?:disponivel|atual|de|:|e|é|\\s)*$")) continue;
            try {
                String number = m.group(1).replace(".", "");
                long value = new java.math.BigDecimal(number.replace(',', '.')).movePointRight(2).longValueExact();
                if (value <= 0 || (found != null && found != value)) return null;
                found = value;
            } catch (NumberFormatException | ArithmeticException e) { return null; }
        }
        return found;
    }
    private static String party(String text, boolean purchase, boolean incoming) {
        String lead = purchase ? "(?:\\bem\\s+|estabelecimento\\s*:\\s*)" : incoming ? "(?:\\bde\\s+|(?:remetente|pagador)\\s*:\\s*)" : "(?:\\bpara\\s+|(?:destinat[aá]rio|favorecido)\\s*:\\s*)";
        Matcher m = Pattern.compile(lead + "([^\\n]+)", FLAGS).matcher(text);
        while (m.find()) {
            String name = m.group(1).split("(?i)\\s+(?:às|as\\s+\\d|via\\s+pix|no\\s+valor|no\\s+seu|com\\s+(?:o|seu)\\s+cartão|e\\s+(?:seu\\s+)?saldo)\\b|[.!?](?:\\s|$)", 2)[0].trim();
            if (!purchase && !incoming) name = name.split(",|(?i)\\s+foi\\s+(?:realizada|enviada|concluída)\\b", 2)[0].trim();
            if (name.isEmpty() || name.contains("R$") || name.length() > 100) continue;
            return purchase ? name : (Pattern.compile("\\bpix\\b").matcher(fold(text)).find() ? (incoming ? "Pix de " : "Pix para ") : (incoming ? "Transferência de " : "Transferência para ")) + name;
        }
        return null;
    }
    public static final class Result {
        public final String name; public final long cents;
        Result(String name, long cents) { this.name = name; this.cents = cents; }
    }
}
