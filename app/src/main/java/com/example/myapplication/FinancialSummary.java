package com.example.myapplication;

/** Resultado soma todas as entradas e saídas; a natureza só detalha a movimentação. */
public final class FinancialSummary {
    public long receipts, income, accountOut, expenses, creditPurchases, invoicePayments, unclassified;
    public void add(long cents, TransactionKind kind, boolean incomplete) {
        if (cents == 0) return;
        if (cents > 0) receipts += cents;
        if (kind == TransactionKind.CARD_UNSPECIFIED || kind == TransactionKind.UNKNOWN) {
            unclassified += Math.abs(cents);
        }
        if (kind.affectsAccount()) {
            if (cents > 0) income += cents;
            else accountOut -= cents;
        }
        if (cents < 0) expenses -= cents;
        if (cents < 0 && kind == TransactionKind.CREDIT_PURCHASE) creditPurchases -= cents;
        if (cents < 0 && kind == TransactionKind.INVOICE_PAYMENT) invoicePayments -= cents;
    }
    public long result() { return receipts - expenses; }
    public long accountNet() { return income - accountOut; }
}
