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

Uma identidade de notificação + horário de evento evita repetir a mesma captura. Notificações distintas do Nubank e do Gmail sobre uma mesma saída ainda podem gerar dois registros: não são eliminadas apenas por valor, pois isso poderia perder duas transferências legítimas iguais. O resultado do resumo é a diferença dos lançamentos, não o saldo bancário. Planejamento ainda contém metas ilustrativas.

A leitura depende do acesso concedido e do conteúdo disponibilizado pelo Android. Pausar interrompe novas capturas; não há importação automática de histórico. Forçar parada ou restrições do aparelho podem interromper o serviço.

## Validação

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
# Com emulador/aparelho:
./gradlew :app:connectedDebugAndroidTest
```

O código principal e os testes compilaram diretamente com javac; 23 testes unitários de extração passaram. Os testes instrumentados incluem a composição dos campos Android, reprocessamento de registros salvos, persistência, proteção de edições e exclusão. Esses testes em aparelho e o build APK ainda não foram executados neste ambiente, onde o Gradle encontrou restrição ao inicializar sockets. A compatibilidade com os textos reais reportados depende da validação no aparelho.

Referência Android: https://developer.android.com/reference/android/app/Notification.html

O formato informado de e-mail aceita valor em outra linha, uma ou duas casas decimais e destinatário seguido de vírgula/dados bancários. Registros incompletos já salvos podem ser reprocessados; e-mails anteriormente ignorados não estavam salvos e exigem uma nova notificação para captura.
