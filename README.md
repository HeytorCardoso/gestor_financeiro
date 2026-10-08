# Finna — frontend Android

Protótipo de gestor financeiro em Java com componentes nativos Android e Material. Abra esta pasta no Android Studio, aguarde a sincronização do Gradle e execute o módulo `app` em um aparelho ou emulador (Android 7.0 / API 24 ou superior).

## Telas

- **Início:** saldo, receitas, despesas, distribuição dos gastos e últimas movimentações.
- **Extrato:** lista com filtros para receitas e despesas.
- **Planejar:** reserva de emergência e orçamento ilustrativo por categoria.
- **Perfil:** informações sobre o protótipo.

O botão **Novo lançamento** abre um formulário com descrição, valor, tipo e categoria. Os lançamentos atualizam o resumo e o extrato durante a sessão, e são preservados em recriações da tela, como rotação. Não há banco de dados, autenticação, conexão bancária ou backend. Metas e limites são exemplos fixos.

A interface está em `app/src/main/java/com/example/myapplication/MainActivity.java`. O tema e o nome do app estão em `app/src/main/res/values/`. A interface é construída programaticamente em Java; não utiliza o layout XML inicial do projeto.

Para compilar pela linha de comando, configure um JDK compatível com o Gradle do projeto e o SDK Android e execute:

```bash
./gradlew :app:assembleDebug
```
# gestor_financeiro
