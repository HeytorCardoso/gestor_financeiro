package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class FinanceRulesTest {
    @Test public void creditPurchaseAndInvoiceBothCountAsCapturedOutgoings() {
        FinancialSummary total=new FinancialSummary();
        total.add(-10000,TransactionKind.CREDIT_PURCHASE,false);
        total.add(-10000,TransactionKind.INVOICE_PAYMENT,false);
        assertEquals(20000,total.expenses); assertEquals(10000,total.accountOut);
        assertEquals(-20000,total.result());
        assertEquals(10000,total.creditPurchases); assertEquals(10000,total.invoicePayments);
        assertEquals(-10000,total.accountNet());
    }
    @Test public void debitAndTransfersAffectAccountAndSpending() {
        FinancialSummary total=new FinancialSummary();
        total.add(50000,TransactionKind.ACCOUNT_TRANSFER,false);
        total.add(-10000,TransactionKind.ACCOUNT_TRANSFER,false);
        total.add(-2500,TransactionKind.ACCOUNT_EXPENSE,false);
        assertEquals(12500,total.expenses); assertEquals(12500,total.accountOut);
        assertEquals(37500,total.accountNet());
    }
    @Test public void uncertainCardModalityIsNotGuessed() {
        NotificationParser.Result r=NotificationParser.parse("Compra aprovada", "R$ 35,00 em Loja");
        assertEquals(TransactionKind.CARD_UNSPECIFIED,r.kind);
        FinancialSummary total=new FinancialSummary();total.add(r.cents,r.kind,false);
        assertEquals(3500,total.unclassified); assertEquals(3500,total.expenses); assertEquals(-3500,total.result()); assertEquals(0,total.accountOut);
    }
    @Test public void recognizesExplicitCreditAndDebit() {
        assertEquals(TransactionKind.CREDIT_PURCHASE,NotificationParser.parse("Compra aprovada no crédito", "R$ 35,00 em Loja").kind);
        assertEquals(TransactionKind.ACCOUNT_EXPENSE,NotificationParser.parse("Compra aprovada no débito", "R$ 35,00 em Loja").kind);
    }
    @Test public void invoicePaymentConfirmationIsNotIncome() {
        NotificationParser.Result r=NotificationParser.parse("Pagamento recebido", "Recebemos o pagamento da sua fatura de R$ 120,00.");
        assertNotNull(r);assertEquals(TransactionKind.INVOICE_PAYMENT,r.kind);assertEquals(-12000,r.cents);
        assertNull(NotificationParser.parse("Sua fatura está disponível", "Faça o pagamento de R$ 120,00 até amanhã."));
    }
    @Test public void missingAmountsStayOutOfAllTotals() {
        FinancialSummary total=new FinancialSummary();total.add(0,TransactionKind.CREDIT_PURCHASE,true);
        assertEquals(0,total.expenses);assertEquals(0,total.creditPurchases);
    }
    @Test public void invoiceCountsAsExpenseEvenWithoutPurchaseHistory() {
        FinancialSummary total=new FinancialSummary();total.add(-50000,TransactionKind.INVOICE_PAYMENT,false);
        assertEquals(50000,total.accountOut);assertEquals(50000,total.expenses);assertEquals(-50000,total.result());
    }
    @Test public void creditLimitMentionDoesNotDeterminePurchaseModality() {
        assertEquals(TransactionKind.CARD_UNSPECIFIED,NotificationParser.parse("Compra aprovada", "R$ 35,00 em Loja. Limite de crédito disponível: R$ 100,00").kind);
    }
    @Test public void unclassifiedIncomeAndExpenseAlreadyAffectResult() {
        FinancialSummary total=new FinancialSummary();
        total.add(5000,TransactionKind.UNKNOWN,false);
        total.add(-1000,TransactionKind.UNKNOWN,false);
        total.add(-2000,TransactionKind.CARD_UNSPECIFIED,false);
        assertEquals(5000,total.receipts); assertEquals(3000,total.expenses);
        assertEquals(2000,total.result()); assertEquals(0,total.accountNet());
    }
    @Test public void classifyingPurchaseDoesNotCountItTwiceOrChangeResult() {
        FinancialSummary before=new FinancialSummary();before.add(-1000,TransactionKind.CARD_UNSPECIFIED,false);
        FinancialSummary after=new FinancialSummary();after.add(-1000,TransactionKind.CREDIT_PURCHASE,false);
        assertEquals(before.result(),after.result());assertEquals(1000,after.expenses);
        assertEquals(0,after.unclassified);assertEquals(0,after.accountOut);
    }
    @Test public void classifyingAsInvoiceKeepsExpenseAndResult() {
        FinancialSummary before=new FinancialSummary();before.add(-1000,TransactionKind.UNKNOWN,false);
        FinancialSummary after=new FinancialSummary();after.add(-1000,TransactionKind.INVOICE_PAYMENT,false);
        assertEquals(-1000,before.result());assertEquals(before.result(),after.result());assertEquals(1000,after.expenses);assertEquals(1000,after.accountOut);
    }
    @Test public void allKnownAmountsCountRegardlessOfNatureOrIncompleteFlag() {
        for(TransactionKind kind:TransactionKind.values()) {
            FinancialSummary total=new FinancialSummary();
            total.add(-1000,kind,true);
            assertEquals(kind.label,1000,total.expenses);
            assertEquals(kind.label,-1000,total.result());
        }
    }
}
