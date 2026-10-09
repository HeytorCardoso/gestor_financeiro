# Finna — gestor financeiro Android em Java

Captura novas notificações do Nubank (`com.nu.production`) e avisos de transferência enviada do Nubank no Gmail (`com.google.android.gm`). Não acessa contas bancárias ou caixas de e-mail, nem utiliza conexão com servidores. Não possui permissão de internet.

## Usar

1. Execute o módulo `app` pelo Android Studio (Android 7/API 24 ou superior).
2. Abra **Captura → Gerenciar acesso no Android** e autorize o Finna.
3. Mantenha as notificações de Nubank e Gmail habilitadas e aguarde uma nova movimentação.
4. O lançamento reconhecido entra diretamente no extrato e nos totais, sem aprovação.
5. Toque no registro para complementar observações, mudar descrição, valor, tipo e categoria, ou **Apagar lançamento**.

No Gmail, recebimentos são ignorados para que não sejam importados outra vez por e-mail. A tela Captura informa se o serviço recebeu uma notificação Gmail e se identificou uma saída. Essa informação de diagnóstico não grava o texto de e-mails rejeitados.

## Extração

Título, título expandido, texto curto, texto expandido e linhas complementares são considerados em conjunto. O extrator reconhece variações de compras, Pix enviado/recebido, transferências e pagamentos de boleto; não exige uma frase inteira idêntica ao exemplo ou um horário específico. Valores repetidos entre título e corpo não tornam o registro ambíguo. Saldo e limite explicitamente identificados são separados do valor da operação.

Para Gmail, o filtro aceita a identificação textual de Nubank/Nu junto de uma transferência enviada. Quando o remetente não aparece, aceita especificamente o formato informado pelo usuário: assunto “Transferência realizada com sucesso”, frase “A transferência para … foi realizada com sucesso” e campo “Valor enviado:”. Esse formato é um filtro textual e não autentica que o e-mail veio do Nubank. Avisos recebidos e operações recusadas, canceladas, agendadas ou pendentes não são lançados como saídas confirmadas. Resumos de grupos de várias mensagens não são tratados como uma única operação: a captura depende das notificações individuais do Gmail. Não há autenticação do remetente: o nome exibido é um filtro textual.

Textos realmente sem tipo/valor identificável continuam no extrato como incompletos, fora dos totais. Ao abrir o app, registros incompletos salvos são reprocessados pelo extrator atualizado. Registros alterados manualmente e apagados são preservados; não se tenta adivinhar valores indisponíveis. Sem exemplos reais do aparelho, a compatibilidade de cada variante de notificação não pode ser garantida.

## Persistência e identidade

SQLite privado armazena dados monetários em centavos e preserva os lançamentos entre execuções. Migrações das versões anteriores mantêm os dados existentes. Edições manuais não são sobrescritas por atualizações da notificação. Apagar remove o registro do extrato e limpa seu texto, descrição, valor e observações, mantendo um marcador de identidade para evitar sua reimportação. Backups desses dados estão desabilitados.

Uma identidade de notificação + horário de evento evita repetir a mesma captura. Notificações distintas do Nubank e Gmail são comparadas e sinalizadas conforme as regras abaixo; nenhuma é eliminada apenas por valor. O resumo da conta representa apenas entradas menos saídas capturadas da conta, não o saldo bancário. Planejamento ainda contém metas ilustrativas.

A leitura depende do acesso concedido e do conteúdo disponibilizado pelo Android. Pausar interrompe novas capturas; não há importação automática de histórico. Forçar parada ou restrições do aparelho podem interromper o serviço.

## Validação

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
# Com emulador/aparelho:
./gradlew :app:connectedDebugAndroidTest
```

O código principal e os testes compilaram diretamente com javac; 41 testes unitários de extração, classificação, totais e comparação passaram. Os testes instrumentados incluem a composição dos campos Android, reprocessamento de registros salvos, persistência, proteção de edições e exclusão. Esses testes em aparelho e o build APK ainda não foram executados neste ambiente. O Gradle encontrou restrição ao inicializar sockets e a tentativa de iniciar o ADB também foi bloqueada (`Operation not permitted`). A compatibilidade com os textos reais reportados depende da validação no aparelho.

Referência Android: https://developer.android.com/reference/android/app/Notification.html

O formato informado de e-mail aceita valor em outra linha, uma ou duas casas decimais e destinatário seguido de vírgula/dados bancários. Registros incompletos já salvos podem ser reprocessados; e-mails anteriormente ignorados não estavam salvos e exigem uma nova notificação para captura.

## Duplicidade entre fontes

O app compara saídas identificadas como Pix/transferência: mesmo valor em centavos, fontes diferentes (Nubank e Gmail), recebidas em até 30 minutos e destinatários iguais após normalização de acentos, espaços e pontuação. Se ambos os destinatários forem conhecidos e diferentes, não sinaliza. Se faltar um destinatário, sinaliza um indício mais fraco. A janela usa o horário de captura, portanto e-mails muito atrasados podem não ser associados.

Os registros entram automaticamente. Uma faixa no início/extrato e o filtro **Duplicatas** permitem comparar as duas capturas. Até a decisão, ambas continuam nos totais, com aviso explícito. **É a mesma transação · Unir** mantém o lançamento aberto nos totais e associa a outra captura, preservando seus textos, categorias e observações. O editor permite **Desfazer união**. **São operações diferentes** mantém ambos e salva a decisão para esse par de registros. Em grupos unidos, desfazer restaura todas as fontes associadas; apagar apaga o grupo e mantém os marcadores para não reimportar notificações antigas. Não há identificador bancário confiável disponível para eliminar automaticamente casos incertos.

## Conta, crédito e fatura

- **Pix / transferência:** movimenta a conta; saídas entram nos gastos capturados.
- **Despesa na conta:** débitos e boletos; movimenta a conta e entra nos gastos.
- **Compra no crédito:** entra nos gastos, sem alterar a movimentação da conta no momento da compra.
- **Pagamento de fatura:** entra nas saídas da conta, nos gastos capturados e no resultado dos lançamentos.
- **Cartão sem modalidade / natureza não identificada:** já entra no resultado dos lançamentos: saídas contam como gastos e entradas como receitas. Continua identificado como valor a classificar; não altera o fluxo da conta enquanto a modalidade/natureza não for conhecida.

Exemplo: compra no crédito de R$ 100 e pagamento da fatura de R$ 100 produzem R$ 200 em gastos capturados e resultado de -R$ 200, na ausência de receitas. Essa soma por lançamento é a regra solicitada pelo usuário, mesmo quando compra e fatura se referem ao mesmo consumo. A visão da conta continua mostrando R$ 100 de saída, pois a compra no crédito não movimenta a conta nesse momento. As visões abrangem o histórico capturado, sem calendário de fechamento, parcelas ou saldo de fatura a pagar.

A modalidade só é inferida quando a notificação indica crédito ou débito; uma compra aprovada genérica não é presumida como crédito. No editor, **Natureza financeira** permite corrigir essa classificação e **Destinatário / remetente** ajusta a comparação de duplicatas. Categorias e orçamentos de exemplo usam todas as saídas, incluindo pagamentos de fatura. Os filtros do extrato separam conta, crédito, faturas, itens a classificar e possíveis duplicatas.

O banco versão 4 acrescenta natureza, destinatário e decisões de duplicidade sem alterar valores, descrições e observações já editados. Os testes instrumentados `FinanceStoreTest` cobrem migração da versão 3, união/desunião, preservação das edições, exclusão, ordem inversa de captura e decisões persistentes. Compilaram com javac, mas ainda precisam de emulador/aparelho para execução.

O indicador principal **Resultado dos lançamentos** considera todas as receitas e gastos com valor reconhecido, mesmo sem classificação. A movimentação da conta é exibida separadamente e usa somente naturezas conhecidas. Identificar posteriormente uma fatura não muda sua participação no resultado ou nos gastos; apenas detalha a natureza da movimentação. Categorias/orçamentos também incluem despesas ainda sem modalidade. Registros sem valor extraído continuam fora do cálculo.

A regra dos totais usa o sinal do valor, independentemente da natureza ou classificação: todas as entradas somam receitas e todas as saídas somam gastos, inclusive faturas. Registros apagados ou unidos a outro lançamento continuam fora da lista contabilizada. Sem valor conhecido, não há quantia a somar. Não é necessário recapturar os lançamentos existentes para atualizar os totais.
