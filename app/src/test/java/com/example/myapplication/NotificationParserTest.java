package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationParserTest {
    @Test public void recognizesPurchase() {
        NotificationParser.Result r = NotificationParser.parse("Compra de R$ 1.234,56 aprovada em Mercado às 12:30.");
        assertNotNull(r); assertEquals(-123456, r.cents); assertEquals("Mercado", r.name);
    }
    @Test public void recognizesPixDirections() {
        assertEquals(-1050, NotificationParser.parse("Você enviou R$ 10,50 para Ana via Pix.").cents);
        assertEquals(1050, NotificationParser.parse("Você recebeu R$ 10,50 de Ana via Pix.").cents);
    }
    @Test public void recognizesBoleto() {
        assertEquals(-9900, NotificationParser.parse("O pagamento do seu boleto no valor de R$ 99,00 foi realizado com sucesso.").cents);
    }
    @Test public void rejectsAmbiguousOrDeclinedMessages() {
        assertNull(NotificationParser.parse("Compra de R$ 10,00 recusada em Loja às 10:30."));
        assertNull(NotificationParser.parse("Seu limite é R$ 1.000,00"));
        assertNull(NotificationParser.parse("Você recebeu R$ 0,00 de Ana via Pix."));
        assertNull(NotificationParser.parse("Você recebeu R$ 15,00 de Ana via Pix. Saldo R$ 20,00"));
    }
    @Test public void toleratesNotificationWhitespace() {
        assertEquals(100, NotificationParser.parse("Você recebeu R$\u00a01,00 de Ana via Pix.\n").cents);
    }
}
