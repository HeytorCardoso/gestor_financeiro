package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class GmailTransferParserTest {
    @Test public void sentTransferBecomesExpense() {
        NotificationParser.Result r = GmailTransferParser.parse("Nubank", "Transferência enviada. Você enviou R$ 1.234,56 para Ana via Pix.");
        assertNotNull(r); assertEquals(-123456, r.cents);
    }
    @Test public void receivedMailIsAlwaysIgnored() {
        assertFalse(GmailTransferParser.accepts("Nubank", "Você recebeu R$ 20,00 de Ana via Pix."));
        assertFalse(GmailTransferParser.accepts("Nubank", "Pix recebido. Transferência enviada anteriormente."));
    }
    @Test public void unrelatedEmailIsIgnored() {
        assertFalse(GmailTransferParser.accepts("Outra empresa", "Transferência enviada de R$ 20,00."));
        assertFalse(GmailTransferParser.accepts("Nubank", "Sua fatura de R$ 20,00 está disponível."));
        assertFalse(GmailTransferParser.accepts("Nubank", "Compra aprovada de R$ 20,00."));
    }
    @Test public void uncompletedTransfersAreIgnored() {
        for (String body : new String[]{"Pix enviado não realizado", "Transferência enviada foi cancelada", "Pix enviado agendado", "Transferência enviada pendente", "Pix enviado estornado"})
            assertFalse(body, GmailTransferParser.accepts("Nubank", body));
    }
    @Test public void missingOrAmbiguousAmountIsIncomplete() {
        assertTrue(GmailTransferParser.accepts("Nubank", "Transferência enviada"));
        assertNull(GmailTransferParser.parse("Nubank", "Transferência enviada"));
        assertEquals(-1000, GmailTransferParser.parse("Nubank", "Pix enviado: R$ 10,00. Saldo R$ 20,00.").cents);
    }
    @Test public void repeatedAmountAcrossTitleAndBodyIsAccepted() {
        assertEquals(-1000, GmailTransferParser.parse("Nubank · Pix enviado R$ 10,00", "Você enviou R$ 10,00 para Ana via Pix.").cents);
    }
    @Test public void acceptsNuDisplayNameAndTransferSubject() {
        assertEquals(-1000, GmailTransferParser.parse("Nu", "Você fez uma transferência de R$ 10,00 para Ana.").cents);
        assertTrue(GmailTransferParser.accepts("Nu Pagamentos", "Transferência realizada de R$ 10,00 para Ana."));
    }
    @Test public void extractsRecipientInsteadOfGenericDescription() {
        assertEquals("Pix para Ana", GmailTransferParser.parse("Nubank", "Você enviou R$ 10,00 para Ana via Pix.").name);
    }
    @Test public void emailFooterWithNaoIsNotRejected() {
        assertEquals(-1000, GmailTransferParser.parse("Nubank", "Pix enviado. Valor: R$ 10,00. Não responda a este e-mail.").cents);
    }
}
