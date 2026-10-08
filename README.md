# Finna — gestor financeiro Android em Java

Lê novas notificações do Nubank (`com.nu.production`) usando o `NotificationListenerService` do Android, sem login bancário, API do banco ou acesso à conta. O app não possui permissão de internet.

## Ativar no aparelho

1. Abra o projeto no Android Studio e execute o módulo `app` (API 24 ou superior).
2. Abra **Captura → Gerenciar acesso no Android → Abrir configurações**.
3. Autorize o **Finna** no acesso às notificações e volte ao app.
4. Aguarde uma nova notificação real do Nubank. O serviço funciona sem a tela do app aberta, desde que o Android mantenha o acesso e o serviço habilitados.
5. Confira o extrato. Mensagens desconhecidas aparecem em **Aguardando revisão**, onde podem ser preenchidas manualmente ou ignoradas. Toque nos lançamentos reconhecidos para categorizar.

É possível pausar a captura sem revogar a autorização. Notificações recebidas durante a pausa não são importadas posteriormente. O app começa vazio, não importa notificações antigas e não mistura dados simulados com reais. Planejamento ainda contém metas e limites ilustrativos.

## Formatos reconhecidos

Os quatro formatos foram fornecidos pelo usuário, não confirmados em aparelho com Nubank:

- `Compra de R$ 39,90 aprovada em Padaria às 12:30.`
- `Você enviou R$ 10,00 para Ana via Pix.`
- `Você recebeu R$ 10,00 de Ana via Pix.`
- `O pagamento do seu boleto no valor de R$ 99,00 foi realizado com sucesso.`

São aceitos separadores de milhar, variação de maiúsculas e espaços. Textos ambíguos, compras recusadas, conteúdo oculto e formatos diferentes ficam pendentes e não entram nos totais. O horário armazenado é o da notificação recebida pelo Android, não uma confirmação da data contábil da transação.

## Armazenamento e limites

SQLite privado salva texto, descrição, centavos, categoria, horário e identidade da notificação. Backups e transferências automáticas desses dados estão excluídos. A autorização Android dá acesso amplo às notificações, mas o serviço retorna imediatamente para aplicativos diferentes do Nubank.

A identidade da notificação e seu horário de evento impedem a reimportação do mesmo evento. Atualizações de uma notificação pendente podem completar seu conteúdo. Notificações distintas referentes à mesma transação não podem ser deduplicadas com certeza sem identificador bancário; revise pagamentos de fatura e compras para evitar dupla contagem. Se o banco reutilizar a mesma identidade e horário para transações diferentes, a segunda não será importada automaticamente.

Não é possível obter valores que o sistema ou o Nubank ocultem. O resultado exibido representa entradas menos saídas reconhecidas, não o saldo bancário. Reinício forçado, restrições de bateria e políticas do aparelho podem interromper o serviço; confira o status e o acesso nas configurações. Em instalações externas, alguns aparelhos exigem liberar configurações restritas antes de habilitar o acesso às notificações.

## Código e testes

- `MainActivity.java`: interface, autorização, revisão e status.
- `NubankNotificationService.java`: filtro por pacote, leitura do conteúdo e captura.
- `NotificationParser.java`: extrator conservador dos quatro formatos.
- `CaptureStore.java`: persistência, deduplicação e revisão.
- `NotificationParserTest.java`: testes unitários de interpretação.
- `CaptureStoreTest.java`: teste instrumentado de persistência e deduplicação em banco separado.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
# Com emulador/aparelho conectado:
./gradlew :app:connectedDebugAndroidTest
```

Referência: https://developer.android.com/reference/android/service/notification/NotificationListenerService

## Validação nesta implementação

O código Java principal e os novos testes compilaram diretamente com `javac` e as bibliotecas Android disponíveis. Os 5 testes unitários do extrator passaram com JUnit. O build Gradle foi bloqueado pelo ambiente ao inicializar os sockets de coordenação (`Could not determine a usable wildcard IP`), portanto o APK e o teste instrumentado ainda precisam ser validados no Android Studio/aparelho. A captura real com Nubank ainda não foi exercitada neste ambiente.
