package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class CategoryRulesTest {
    @Test public void merchantIdentitySurvivesDifferentValuesAndCardModality() {
        assertEquals(CategoryRules.identity(NotificationParser.parse("Compra aprovada no crédito","R$ 20,00 em Café Central")),
                     CategoryRules.identity(NotificationParser.parse("Compra aprovada no débito","R$ 35,00 em CAFE CENTRAL")));
    }
    @Test public void matchesRecipientAcrossBankAndGmail() {
        String mail="Olá, Fulano.\nA transferência para Ana, banco de destino, foi realizada com sucesso.\nValor enviado:\nR$ 10,00";
        assertEquals(CategoryRules.identity(NotificationParser.parse("Você enviou R$ 10,00 para Ana via Pix.")),
                     CategoryRules.identity(GmailTransferParser.parse("Transferência realizada com sucesso",mail)));
    }
    @Test public void incomeAndOutgoingRulesAreIndependent() {
        assertNotEquals(CategoryRules.identity(NotificationParser.parse("Você enviou R$ 10,00 para Ana via Pix.")),
                        CategoryRules.identity(NotificationParser.parse("Você recebeu R$ 10,00 de Ana via Pix.")));
    }
    @Test public void genericDescriptionsDoNotCreateGlobalRules() {
        assertEquals("",CategoryRules.identity(NotificationParser.parse("Compra aprovada de R$ 10,00.")));
        assertEquals("",CategoryRules.identity(NotificationParser.parse("Pagamento da fatura de R$ 10,00 confirmado.")));
        assertEquals("",CategoryRules.identity(null));
    }
    @Test public void differentMerchantsDoNotShareRule() {
        assertNotEquals(CategoryRules.identity(NotificationParser.parse("Compra aprovada de R$ 10,00 em Loja A.")),
                        CategoryRules.identity(NotificationParser.parse("Compra aprovada de R$ 10,00 em Loja B.")));
    }
}
