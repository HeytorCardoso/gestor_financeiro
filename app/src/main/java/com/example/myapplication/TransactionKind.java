package com.example.myapplication;

/** Natureza financeira independente da categoria e do sinal monetário. */
public enum TransactionKind {
    ACCOUNT_TRANSFER("Pix / transferência"),
    ACCOUNT_EXPENSE("Despesa na conta"),
    CREDIT_PURCHASE("Compra no crédito"),
    CARD_UNSPECIFIED("Cartão sem modalidade"),
    INVOICE_PAYMENT("Pagamento de fatura"),
    UNKNOWN("Natureza não identificada");

    public final String label;
    TransactionKind(String label) { this.label = label; }
    public boolean affectsAccount() { return this == ACCOUNT_TRANSFER || this == ACCOUNT_EXPENSE || this == INVOICE_PAYMENT; }
    public static TransactionKind fromStorage(String value) {
        try { return valueOf(value); } catch (IllegalArgumentException | NullPointerException ex) { return UNKNOWN; }
    }
    public static TransactionKind fromLabel(String label) {
        for (TransactionKind kind : values()) if (kind.label.equals(label)) return kind;
        return UNKNOWN;
    }
}
