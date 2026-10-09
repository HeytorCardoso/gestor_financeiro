package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class DuplicateMatcherTest {
    private String reason(String source,long amount,long delay,TransactionKind kind,String party) {
        return DuplicateMatcher.reason("Nubank",-1000,1000,TransactionKind.ACCOUNT_TRANSFER,"José da Silva",source,amount,1000+delay,kind,party);
    }
    @Test public void matchesSourcesAmountNormalizedRecipientAndWindow() {
        assertNotNull(reason("Gmail · Nubank",-1000,60000,TransactionKind.ACCOUNT_TRANSFER,"JOSE  DA SILVA"));
        assertNotNull(reason("Gmail · Nubank",-1000,DuplicateMatcher.WINDOW_MS,TransactionKind.ACCOUNT_TRANSFER,"José da Silva"));
    }
    @Test public void differingKnownRecipientsAreNotCandidates() {
        assertNull(reason("Gmail · Nubank",-1000,60000,TransactionKind.ACCOUNT_TRANSFER,"Ana"));
    }
    @Test public void doesNotMatchOnlyOnAmountOrSameSource() {
        assertNull(reason("Nubank",-1000,60000,TransactionKind.ACCOUNT_TRANSFER,"José da Silva"));
        assertNull(reason("Gmail · Nubank",-1001,60000,TransactionKind.ACCOUNT_TRANSFER,"José da Silva"));
        assertNull(reason("Gmail · Nubank",-1000,DuplicateMatcher.WINDOW_MS+1,TransactionKind.ACCOUNT_TRANSFER,"José da Silva"));
    }
    @Test public void missingRecipientIsExplicitlyWeakSuggestion() {
        assertTrue(reason("Gmail · Nubank",-1000,60000,TransactionKind.ACCOUNT_TRANSFER,"").contains("Falta o destinatário"));
    }
    @Test public void cardAndInvoiceAreNeverDuplicatesOfTransfer() {
        assertNull(reason("Gmail · Nubank",-1000,60000,TransactionKind.CREDIT_PURCHASE,"José da Silva"));
        assertNull(reason("Gmail · Nubank",-1000,60000,TransactionKind.INVOICE_PAYMENT,"José da Silva"));
    }
    @Test public void incomeIsNotComparedAsGmailOutgoingTransfer() {
        assertNull(reason("Gmail · Nubank",1000,60000,TransactionKind.ACCOUNT_TRANSFER,"José da Silva"));
    }
}
