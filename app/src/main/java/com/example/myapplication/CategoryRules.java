package com.example.myapplication;

/** Identidades exatas normalizadas, independentes de valor, fonte e descrição editada. */
public final class CategoryRules {
    public static final String[] CATEGORIES = {"Alimentação","Casa","Mobilidade","Trabalho","Saúde","Lazer","Outros"};
    public static String identity(NotificationParser.Result parsed) {
        if (parsed == null) return "";
        if (parsed.kind == TransactionKind.ACCOUNT_TRANSFER) {
            String party=normalize(parsed.counterparty);
            return party.isEmpty() ? "" : (parsed.cents > 0 ? "transfer-in:" : "transfer-out:") + party;
        }
        String merchant=normalize(parsed.name);
        if (parsed.kind == TransactionKind.INVOICE_PAYMENT || merchant.isEmpty()
                || merchant.equals("compra no cartao") || merchant.equals("pagamento de boleto")
                || parsed.kind == TransactionKind.UNKNOWN) return "";
        return "merchant:" + merchant;
    }
    public static String normalize(String text) {
        return NotificationParser.fold(text).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");
    }
    public static boolean valid(String category) {
        for(String candidate:CATEGORIES) if(candidate.equals(category)) return true;
        return false;
    }
}
