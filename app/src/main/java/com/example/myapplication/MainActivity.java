package com.example.myapplication;

import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

/** Interface de lançamentos capturados localmente das notificações. */
public class MainActivity extends AppCompatActivity {
    private final int ink = Color.rgb(26, 45, 40), green = Color.rgb(26, 112, 83);
    private final int muted = Color.rgb(114, 128, 121), background = Color.rgb(246, 248, 245);
    private LinearLayout root, content, navigation;
    private String page = "Início", filter = "Todas";
    private boolean hidden = false;
    private final StatementQuery statementQuery=new StatementQuery();
    private LinearLayout statementRows;
    private TextView statementTotals;
    private com.google.android.material.textfield.TextInputEditText searchInput;
    private CaptureStore store;
    private long lastRevision = -1;
    private String lastStatus = "";
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (prefs, key) -> runOnUiThread(this::render);
    private final android.os.Handler refreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { public void run() { if (store.revision() != lastRevision || !captureState().equals(lastStatus)) { loadEntries(); render(); } refreshHandler.postDelayed(this, 1500); } };
    private final ArrayList<Entry> entries = new ArrayList<>();
    private FinancialSummary summary = new FinancialSummary();
    private ArrayList<CaptureStore.Duplicate> duplicates = new ArrayList<>();
    private final java.util.HashSet<Long> duplicateIds = new java.util.HashSet<>();
    private final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new CaptureStore(this);
        if (state != null) {
            page = state.getString("page", "Início");
            filter = state.getString("filter", "Todas");
            hidden = state.getBoolean("hidden");
            statementQuery.search=state.getString("search","");
            statementQuery.category=state.getString("category",StatementQuery.ALL_CATEGORIES);
            statementQuery.period=state.getString("period",StatementQuery.PERIODS[0]);
            statementQuery.customStart=state.getLong("rangeStart",0);statementQuery.customEndExclusive=state.getLong("rangeEnd",0);
        }
        loadEntries(); render();
    }
    @Override protected void onResume() {
        super.onResume();
        getSharedPreferences("capture", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(settingsListener);
        store.reprocessIncomplete();
        loadEntries(); render();
        refreshHandler.post(refresh);
    }
    @Override protected void onPause() {
        refreshHandler.removeCallbacks(refresh);
        getSharedPreferences("capture", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(settingsListener);
        super.onPause();
    }
    @Override protected void onDestroy() { store.close(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("search",statementQuery.search);out.putString("category",statementQuery.category);out.putString("period",statementQuery.period);
        out.putLong("rangeStart",statementQuery.customStart);out.putLong("rangeEnd",statementQuery.customEndExclusive);
        super.onSaveInstanceState(out); out.putString("page", page); out.putString("filter", filter); out.putBoolean("hidden", hidden);
    }
    private void loadEntries() {
        lastRevision = store.revision();
        entries.clear();
        summary = new FinancialSummary();
        for (CaptureStore.Record r : store.records(false)) {
            entries.add(new Entry(r)); summary.add(r.cents,r.kind,r.incomplete);
        }
        duplicates = store.possibleDuplicates(); duplicateIds.clear();
        for (CaptureStore.Duplicate d : duplicates) { duplicateIds.add(d.first.id); duplicateIds.add(d.second.id); }
    }
    private boolean authorized() {
        String enabled = android.provider.Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        android.content.ComponentName target = new android.content.ComponentName(this, NubankNotificationService.class);
        for (String item : enabled.split(":")) if (target.equals(android.content.ComponentName.unflattenFromString(item))) return true;
        return false;
    }
    private String captureState() {
        if (!authorized()) return "Acesso às notificações desativado";
        if (getSharedPreferences("capture", MODE_PRIVATE).getBoolean("paused", false)) return "Captura pausada";
        return NubankNotificationService.connected ? "Captura ativa · Nubank + Gmail" : "Acesso autorizado · Aguardando conexão";
    }
    private void openPermission() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        LinearLayout panel = card(background);
        panel.addView(text("Organização no automático", 24, ink, true)); space(panel, 12);
        panel.addView(text("Autorize a leitura de notificações", 16, green, true)); space(panel, 12);
        panel.addView(text("O Android concede acesso às notificações do aparelho. O Finna filtra notificações do Nubank e avisos de transferência enviada do Nubank no Gmail, e salva os dados neste aparelho, sem envio ao banco ou a servidores.\n\nNa próxima tela, habilite o Finna. Você pode revogar o acesso quando quiser.", 15, muted, false));
        space(panel, 24); button(panel, "Abrir configurações do Android", () -> {
            dialog.dismiss();
            try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (android.content.ActivityNotFoundException ex) { Toast.makeText(this, "Configuração indisponível neste aparelho", Toast.LENGTH_LONG).show(); }
        });
        space(panel, 8); TextView cancel = text("Agora não", 14, green, true); cancel.setGravity(Gravity.CENTER); cancel.setPadding(0, dp(16), 0, dp(16)); panel.addView(cancel); cancel.setOnClickListener(v -> dialog.dismiss());
        ScrollView scroll = new ScrollView(this); scroll.addView(panel); dialog.setContentView(scroll); dialog.show();
    }

    private void render() {
        boolean restoreSearch=page.equals("Extrato") && searchInput!=null && searchInput.hasFocus();
        int selection=restoreSearch ? searchInput.getSelectionStart() : 0;
        searchInput=null;statementRows=null;statementTotals=null;
        lastStatus = captureState();
        root = column(); root.setBackgroundColor(background); setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = column(); content.setPadding(dp(24), dp(24), dp(24), dp(24)); scroll.addView(content);
        LinearLayout header = row();
        LinearLayout brand = column(); brand.addView(text("finna", 29, ink, true)); brand.addView(text("Seu dinheiro, com clareza.", 12, muted, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView avatar = text("EU", 14, green, true); avatar.setGravity(Gravity.CENTER); avatar.setBackground(shape(0xffe1eee3, 24));
        header.addView(avatar, new LinearLayout.LayoutParams(dp(46), dp(46))); avatar.setContentDescription("Configurar captura"); avatar.setOnClickListener(v -> go("Captura"));
        content.addView(header); space(content, 28);
        if (page.equals("Início")) home();
        else if (page.equals("Extrato")) statement();
        else if (page.equals("Planejar")) planning();
        else profile();
        navigation = row(); navigation.setPadding(dp(8), dp(10), dp(8), dp(10)); navigation.setBackgroundColor(Color.WHITE);
        String[] labels = {"Início", "Extrato", "Planejar", "Captura"};
        String[] symbols = {"⌂", "≡", "◎", "○"};
        for (int i = 0; i < labels.length; i++) {
            final String target = labels[i];
            TextView item = text(symbols[i] + "\n" + target, 13, page.equals(target) ? green : muted, page.equals(target));
            item.setGravity(Gravity.CENTER); item.setPadding(0, dp(5), 0, dp(5));
            if (page.equals(target)) item.setBackground(shape(0xffeaf2e9, 16));
            navigation.addView(item, new LinearLayout.LayoutParams(0, dp(56), 1)); item.setOnClickListener(v -> go(target));
        }
        root.addView(navigation);
        if(restoreSearch && searchInput!=null) {searchInput.requestFocus();searchInput.setSelection(Math.min(Math.max(selection,0),searchInput.length()));}
    }

    private void home() {
        title("Seu resumo", "Suas notificações viram organização.");
        captureStatus();
        LinearLayout balance = card(green); content.addView(balance); 
        LinearLayout top = row(); top.addView(text("RESULTADO DOS LANÇAMENTOS", 12, 0xffd3e8dd, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView eye = text(hidden ? "Mostrar" : "Ocultar", 12, Color.WHITE, true); top.addView(eye); eye.setPadding(dp(8), dp(10), dp(8), dp(10)); eye.setOnClickListener(v -> { hidden = !hidden; render(); }); balance.addView(top);
        balance.addView(text(hidden ? "••••••" : money.format(summary.result() / 100.0), 34, Color.WHITE, true));
        space(balance, 8); balance.addView(text("Receitas menos gastos · Inclui valores a classificar", 12, 0xffd3e8dd, false));
        space(balance, 8); balance.addView(text("Movimentação da conta: " + (hidden ? "••••" : money.format(summary.accountNet() / 100.0)) + " · Natureza identificada", 12, 0xffd3e8dd, false));
        space(content, 14); LinearLayout metrics = row();
        metrics.addView(metric("↗  Entradas na conta", summary.income / 100.0, green), new LinearLayout.LayoutParams(0, -2, 1));
        Space gap = new Space(this); metrics.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
        metrics.addView(metric("↘  Saídas da conta", summary.accountOut / 100.0, 0xffbd674e), new LinearLayout.LayoutParams(0, -2, 1)); content.addView(metrics);
        space(content, 14);
        LinearLayout spending = card(Color.WHITE); content.addView(spending);
        spending.addView(text("Gastos capturados", 17, ink, true)); space(spending, 6);
        spending.addView(text(hidden ? "••••" : money.format(summary.expenses / 100.0), 27, green, true));
        spending.addView(text("Soma todas as saídas capturadas, incluindo compras, faturas e valores sem classificação.", 12, muted, false));
        space(spending, 12);
        spending.addView(text("Compras no crédito: " + (hidden ? "••••" : money.format(summary.creditPurchases / 100.0)), 14, ink, true));
        spending.addView(text("Faturas pagas: " + (hidden ? "••••" : money.format(summary.invoicePayments / 100.0)) + " · incluídas nas saídas da conta", 12, muted, false));
        if (summary.unclassified > 0) {
            space(spending, 10); spending.addView(text("A classificar: " + (hidden ? "••••" : money.format(summary.unclassified / 100.0)) + ". Já incluídos no resultado; a classificação define como afetam a conta.", 13, 0xff9b6b22, true));
        }
        duplicateNotice();
        space(content, 18); button(content, "Configurar captura", () -> go("Captura"));
        space(content, 24); section("Para onde vai seu dinheiro", "Capturado");
        LinearLayout expenses = card(Color.WHITE); content.addView(expenses);
        category(expenses, "Alimentação", categoryTotal("Alimentação"), 0xffd5a24c);
        category(expenses, "Casa", categoryTotal("Casa"), 0xff7d9e8c);
        category(expenses, "Mobilidade", categoryTotal("Mobilidade"), 0xff8799b5);
        space(content, 24); section("Últimas movimentações", "Ver todas").setOnClickListener(v -> go("Extrato"));
        LinearLayout transactions = card(Color.WHITE); content.addView(transactions);
        for (int i = 0; i < Math.min(3, entries.size()); i++) transaction(transactions, entries.get(i));
    }

    private void statement() {
        title("Movimentações", "Seu histórico por dia · Toque para editar.");
        LinearLayout controls=card(Color.WHITE);content.addView(controls);
        searchInput=input(controls,"Buscar descrição, destinatário ou observações",statementQuery.search,android.text.InputType.TYPE_CLASS_TEXT);
        searchInput.setSingleLine(true);searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence text,int start,int count,int after) {}
            public void onTextChanged(CharSequence text,int start,int before,int count) {statementQuery.search=text.toString();renderStatementRows();}
            public void afterTextChanged(android.text.Editable editable) {}
        });
        String[] categories=new String[CategoryRules.CATEGORIES.length+1];categories[0]=StatementQuery.ALL_CATEGORIES;
        System.arraycopy(CategoryRules.CATEGORIES,0,categories,1,CategoryRules.CATEGORIES.length);
        com.google.android.material.textfield.MaterialAutoCompleteTextView categoryFilter=dropdown(controls,"Categoria",statementQuery.category,categories);
        categoryFilter.setOnItemClickListener((parent,view,position,id) -> {statementQuery.category=categories[position];renderStatementRows();});
        com.google.android.material.textfield.MaterialAutoCompleteTextView periodFilter=dropdown(controls,"Período",statementQuery.period,StatementQuery.PERIODS);
        TextView dates=text("",12,muted,false);
        periodFilter.setOnItemClickListener((parent,view,position,id) -> {
            if(position==4) selectDateRange(periodFilter);
            else {statementQuery.period=StatementQuery.PERIODS[position];dates.setText("");renderStatementRows();}
        });
        controls.addView(dates);
        if(statementQuery.period.equals(StatementQuery.PERIODS[4])) dates.setText(rangeLabel());
        TextView clear=text("Limpar pesquisa e filtros",13,green,true);clear.setPadding(0,dp(14),0,dp(14));controls.addView(clear);
        clear.setOnClickListener(v -> {statementQuery.clear();filter="Todas";render();});
        space(content,16);
        HorizontalScrollView scroller = new HorizontalScrollView(this); scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = row(); scroller.addView(tabs);
        for (String option : new String[]{"Todas", "Receitas", "Despesas", "Conta", "Crédito", "Faturas", "A classificar", "Duplicatas"}) {
            TextView tab = text(option, 13, filter.equals(option) ? Color.WHITE : green, true);
            tab.setGravity(Gravity.CENTER); tab.setBackground(shape(filter.equals(option) ? green : 0xffe5eee5,16));
            tab.setPadding(dp(16),dp(14),dp(16),dp(14));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.setMargins(0,0,dp(6),0); tabs.addView(tab,lp);
            tab.setOnClickListener(v -> {filter=option;render();});
        }
        content.addView(scroller); space(content,16); duplicateNotice();
        statementTotals=text("",13,green,true);content.addView(statementTotals);space(content,10);
        statementRows=column();content.addView(statementRows);renderStatementRows();
        space(content,20);
    }
    private String rangeLabel() {
        java.text.DateFormat date=java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT,new Locale("pt","BR"));
        return date.format(new java.util.Date(statementQuery.customStart)) + " até " + date.format(new java.util.Date(statementQuery.customEndExclusive-1));
    }
    private void selectDateRange(com.google.android.material.textfield.MaterialAutoCompleteTextView field) {
        com.google.android.material.datepicker.MaterialDatePicker<androidx.core.util.Pair<Long,Long>> picker=
                com.google.android.material.datepicker.MaterialDatePicker.Builder.dateRangePicker().setTitleText("Período do extrato").build();
        field.setText(statementQuery.period,false);
        picker.addOnPositiveButtonClickListener(selection -> {
            statementQuery.customStart=StatementQuery.pickerDayToLocal(selection.first);
            statementQuery.customEndExclusive=StatementQuery.nextDay(StatementQuery.pickerDayToLocal(selection.second));
            statementQuery.period=StatementQuery.PERIODS[4];render();
        });
        picker.show(getSupportFragmentManager(),"statement-range");
    }
    private void renderStatementRows() {
        if(statementRows==null || statementTotals==null) return;
        statementRows.removeAllViews();
        long currentDay=Long.MIN_VALUE,now=System.currentTimeMillis();int count=0;
        LinearLayout group=null;long income=0,outgoings=0;
        for(Entry e:entries) {
            CaptureStore.Record r=e.record;
            String searchable=r.name + " " + r.counterparty + " " + r.notes + " " + r.source + " " + r.kind.label + " " + r.category;
            if(!matchesFilter(e) || !statementQuery.matches(searchable,r.category,r.time,now)) continue;
            long day=StatementQuery.dayStart(r.time);
            if(day!=currentDay) {
                currentDay=day;space(statementRows,12);
                String label=java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL,new Locale("pt","BR")).format(new java.util.Date(day));
                statementRows.addView(text(label,14,ink,true));space(statementRows,8);
                group=card(Color.WHITE);statementRows.addView(group);
            }
            transaction(group,e);count++;
            if(r.cents>0)income+=r.cents;else outgoings-=r.cents;
        }
        statementTotals.setText(count + " lançamentos · Resultado do filtro: " + (hidden ? "••••" : money.format((income-outgoings)/100.0)));
        if(count==0) {
            LinearLayout empty=card(Color.WHITE);statementRows.addView(empty);
            empty.addView(text("Nenhum lançamento encontrado",18,ink,true));space(empty,8);
            empty.addView(text("Experimente outro período, categoria ou termo de pesquisa.",14,muted,false));
        }
    }

    private void showPanel(com.google.android.material.bottomsheet.BottomSheetDialog dialog,ScrollView scroll) {
        dialog.setContentView(scroll);
        dialog.setOnShowListener(d -> {
            android.view.View sheet=dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet!=null) {
                sheet.setBackground(shape(background,26));
                com.google.android.material.bottomsheet.BottomSheetBehavior<android.view.View> behavior=com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
            if (dialog.getWindow()!=null) dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }); dialog.show();
    }
    private boolean matchesFilter(Entry e) {
        if (filter.equals("Receitas")) return e.record.cents>0;
        if (filter.equals("Despesas")) return e.record.cents<0;
        if (filter.equals("Conta")) return e.record.kind.affectsAccount();
        if (filter.equals("Crédito")) return e.record.kind == TransactionKind.CREDIT_PURCHASE;
        if (filter.equals("Faturas")) return e.record.kind == TransactionKind.INVOICE_PAYMENT;
        if (filter.equals("A classificar")) return e.record.incomplete || e.record.kind == TransactionKind.UNKNOWN || e.record.kind == TransactionKind.CARD_UNSPECIFIED;
        if (filter.equals("Duplicatas")) return duplicateIds.contains(e.id);
        return true;
    }
    private void duplicateNotice() {
        if (duplicates.isEmpty()) return;
        space(content,14); LinearLayout notice=card(0xfffaf0dc); content.addView(notice);
        notice.addView(text("Possível duplicidade",17,0xff9b6b22,true)); space(notice,6);
        notice.addView(text("Há registros semelhantes entre Nubank e Gmail. Os totais incluem ambos até você resolver. Nenhum lançamento foi apagado automaticamente.",13,ink,false));
        space(notice,12); button(notice,"Comparar registros (" + duplicates.size() + ")",() -> compareDuplicate(duplicates.get(0),duplicates.get(0).first.id));
        space(content,12);
    }
    private void compareDuplicate(CaptureStore.Duplicate duplicate,long preferred) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog=new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        ScrollView scroll=new ScrollView(this); LinearLayout form=card(background); scroll.addView(form);
        form.addView(text("É a mesma transação?",24,ink,true)); space(form,8);
        form.addView(text(duplicate.reason,14,muted,false));
        CaptureStore.Record kept=duplicate.first.id==preferred ? duplicate.first : duplicate.second;
        CaptureStore.Record other=duplicate.first.id==preferred ? duplicate.second : duplicate.first;
        for (CaptureStore.Record r:new CaptureStore.Record[]{kept,other}) {
            space(form,16); LinearLayout box=card(Color.WHITE); form.addView(box);
            box.addView(text(r.source,13,green,true)); space(box,6);
            box.addView(text(r.name,18,ink,true));
            box.addView(text(hidden ? "••••" : money.format(Math.abs(r.cents)/100.0),22,ink,true));
            box.addView(text(java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(r.time)),12,muted,false));
            if (!r.counterparty.isEmpty()) box.addView(text("Destinatário: " + r.counterparty,13,muted,false));
            box.addView(text("Categoria: " + r.category,13,muted,false));
            if (!r.notes.isEmpty()) box.addView(text("Observações: " + r.notes,13,muted,false));
            space(box,8); box.addView(text(r.raw,12,muted,false));
        }
        space(form,16);
        form.addView(text("Se unir, o lançamento de " + kept.source + " permanece nos totais. Os dados do outro ficam associados e preservados. Você pode desfazer a união no editor.",13,muted,false));
        space(form,14); button(form,"É a mesma transação · Unir",() -> {
            boolean merged=store.merge(kept.id,other.id); loadEntries(); dialog.dismiss(); render();
            Toast.makeText(this,merged ? "Lançamentos unidos. Agora contam uma vez." : "Os registros mudaram. Confira novamente.",Toast.LENGTH_LONG).show();
        });
        space(form,10); button(form,"São operações diferentes",() -> {store.markDifferent(kept.id,other.id);loadEntries();dialog.dismiss();render();});
        space(form,10); TextView close=text("Decidir depois",14,green,true); close.setGravity(Gravity.CENTER); close.setPadding(0,dp(16),0,dp(16)); form.addView(close); close.setOnClickListener(v -> dialog.dismiss());
        showPanel(dialog,scroll);
    }

    private void planning() {
        title("Seu planejamento", "Pequenos hábitos. Grandes conquistas.");
        LinearLayout goal = card(Color.WHITE); content.addView(goal);
        goal.addView(text("◎  Reserva de emergência", 18, ink, true)); space(goal, 12);
        goal.addView(text("R$ 3.000 de R$ 10.000", 16, green, true)); progress(goal, 30, green);
        goal.addView(text("30% do caminho percorrido", 12, muted, false));
        space(content, 24); section("Orçamento por categoria", "Mensal");
        LinearLayout budgets = card(Color.WHITE); content.addView(budgets);
        budget(budgets, "Alimentação", categoryTotal("Alimentação"), 800);
        budget(budgets, "Casa", categoryTotal("Casa"), 1500);
        budget(budgets, "Mobilidade", categoryTotal("Mobilidade"), 300);
        space(content, 20); LinearLayout tip = card(0xffe7eee1); content.addView(tip);
        tip.addView(text("Um futuro mais tranquilo", 17, green, true)); space(tip, 8);
        tip.addView(text("Separar uma parte da sua renda todo mês pode ajudar a construir sua reserva.", 14, ink, false));
        space(tip, 10); tip.addView(text("Metas e limites ilustrativos neste protótipo.", 12, muted, false));
    }

    private void captureStatus() {
        LinearLayout status = card(0xffeee7f7); content.addView(status);
        status.addView(text(captureState(), 15, 0xff7534a6, true)); space(status, 8);
        status.addView(text(store.records(true).size() + " lançamentos com dados incompletos", 12, muted, false));
        space(status, 8); button(status, "Configurar captura", () -> go("Captura")); space(content, 18);
    }
    private void profile() {
        title("Captura automática", "Nubank e transferências enviadas pelo Gmail.");
        captureStatus();
        LinearLayout details = card(Color.WHITE); content.addView(details);
        details.addView(text("Como funciona", 19, ink, true)); space(details, 12);
        details.addView(text("Autorize o Finna nas configurações do Android. Notificações do Nubank e e-mails do Nubank no Gmail sobre transferências enviadas são analisados automaticamente, mesmo com a tela do app fechada.\n\nNão há acesso à conta, senha ou conexão com o banco. Os lançamentos ficam salvos neste aparelho. O histórico anterior à autorização não é importado.", 14, ink, false));
        space(details, 16); button(details, "Gerenciar acesso no Android", this::openPermission);
        space(details, 12);
        boolean paused = getSharedPreferences("capture", MODE_PRIVATE).getBoolean("paused", false);
        button(details, paused ? "Retomar captura" : "Pausar captura", () -> {
            getSharedPreferences("capture", MODE_PRIVATE).edit().putBoolean("paused", !paused).apply();
        });
        space(content, 20);
        LinearLayout gmail = card(Color.WHITE); content.addView(gmail);
        gmail.addView(text("Notificações do Gmail", 17, ink, true)); space(gmail, 8);
        gmail.addView(text(getSharedPreferences("capture", MODE_PRIVATE).getString("gmailStatus", "Ainda não foi recebida uma notificação do Gmail. Confira se os avisos desses e-mails estão ativados no Gmail."), 13, muted, false));
        space(content, 12); content.addView(text("Lançamentos reconhecidos entram automaticamente. Notificações sem dados suficientes aparecem no extrato como incompletas e ficam fora dos totais até você informar o valor. Compras no crédito e pagamentos de fatura são separados automaticamente quando identificados. Possíveis repetições entre fontes são sinalizadas no extrato.", 12, muted, false));
    }
    private void review(Entry entry) { editEntry(entry.record); }

    private com.google.android.material.textfield.TextInputLayout field(LinearLayout parent, String label) {
        com.google.android.material.textfield.TextInputLayout field = new com.google.android.material.textfield.TextInputLayout(this);
        field.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        field.setHint(label); field.setBoxCornerRadii(dp(14), dp(14), dp(14), dp(14));
        field.setBoxStrokeColor(green); field.setDefaultHintTextColor(android.content.res.ColorStateList.valueOf(muted));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(8), 0, dp(8)); parent.addView(field, lp);
        return field;
    }
    private com.google.android.material.textfield.TextInputEditText input(LinearLayout parent, String label, String value, int type) {
        com.google.android.material.textfield.TextInputLayout box = field(parent, label);
        com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(box.getContext());
        input.setTextColor(ink); input.setTextSize(16); input.setInputType(type); input.setText(value); box.addView(input); return input;
    }
    private com.google.android.material.textfield.MaterialAutoCompleteTextView dropdown(LinearLayout parent, String label, String selected, String[] options) {
        com.google.android.material.textfield.TextInputLayout box = field(parent, label);
        box.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU);
        com.google.android.material.textfield.MaterialAutoCompleteTextView input = new com.google.android.material.textfield.MaterialAutoCompleteTextView(box.getContext());
        input.setInputType(0); input.setTextColor(ink); input.setTextSize(16);
        input.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options));
        input.setText(selected, false); box.addView(input); input.setOnClickListener(v -> input.showDropDown()); return input;
    }
    private void editEntry(CaptureStore.Record r) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout form = column(); form.setPadding(dp(24), dp(12), dp(24), dp(28)); form.setBackground(shape(background, 26)); scroll.addView(form);
        TextView handle = text("━━━━", 18, 0xffcbd6cb, true); handle.setGravity(Gravity.CENTER); form.addView(handle);
        space(form, 12); form.addView(text("Detalhes do lançamento", 24, ink, true)); space(form, 6);
        form.addView(text("Já está no seu extrato. Edite quando quiser.", 13, muted, false)); space(form, 14);
        LinearLayout source = card(0xffe8f1e8); form.addView(source);
        source.addView(text(r.source.toUpperCase(new Locale("pt", "BR")) + " · CAPTURA AUTOMÁTICA", 11, green, true)); space(source, 6);
        source.addView(text(java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(r.time)), 12, muted, false));
        if (r.incomplete) { space(source, 8); source.addView(text("Dados incompletos. Informe o valor para incluir nos totais.", 13, ink, false)); }
        space(form, 16);
        if (duplicateIds.contains(r.id)) {
            space(form,12); button(form,"Comparar possível duplicata", () -> {
                dialog.dismiss();
                for (CaptureStore.Duplicate d:duplicates) if (d.first.id==r.id || d.second.id==r.id) { compareDuplicate(d,r.id); break; }
            });
        }
        ArrayList<CaptureStore.Record> linked = store.linkedRecords(r.id);
        if (!linked.isEmpty()) {
            space(form,12); form.addView(text("Fontes associadas · Contadas uma única vez",16,green,true));
            for (CaptureStore.Record l:linked) {
                LinearLayout saved=card(Color.WHITE); space(form,8); form.addView(saved);
                saved.addView(text(l.source + " · " + l.name,14,ink,true));
                saved.addView(text(l.kind.label + " · " + l.category + " · " + (hidden ? "••••" : money.format(Math.abs(l.cents)/100.0)),12,muted,false));
                saved.addView(text(l.raw,12,muted,false));
                if (!l.notes.isEmpty()) saved.addView(text("Observações preservadas: " + l.notes,12,muted,false));
            }
            space(form,8); button(form,"Desfazer união dos lançamentos", () -> {store.undoMerge(r.id);loadEntries();dialog.dismiss();render();});
        }
        int plain = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        com.google.android.material.textfield.TextInputEditText name = input(form, "Descrição", r.name, plain);
        com.google.android.material.textfield.TextInputEditText value = input(form, "Valor (R$)", r.cents == 0 ? "" : java.math.BigDecimal.valueOf(r.cents).abs().movePointLeft(2).toPlainString().replace('.', ','), android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        com.google.android.material.textfield.MaterialAutoCompleteTextView type = dropdown(form, "Tipo", r.cents > 0 ? "Receita" : "Despesa", new String[]{"Despesa", "Receita"});
        String[] natures = new String[TransactionKind.values().length];
        for (int i=0;i<natures.length;i++) natures[i]=TransactionKind.values()[i].label;
        com.google.android.material.textfield.MaterialAutoCompleteTextView nature = dropdown(form,"Natureza financeira",r.kind.label,natures);
        form.addView(text("Todas as saídas entram nos gastos, inclusive faturas. A natureza organiza os detalhes da movimentação.",12,muted,false));
        com.google.android.material.textfield.TextInputEditText counterparty = input(form,"Destinatário / remetente (opcional)",r.counterparty,plain);
        com.google.android.material.textfield.MaterialAutoCompleteTextView category = dropdown(form, "Categoria", r.category, CategoryRules.CATEGORIES);
        CheckBox remember=new CheckBox(this);remember.setText("Usar esta categoria nos próximos lançamentos semelhantes");
        remember.setTextColor(muted);remember.setButtonTintList(android.content.res.ColorStateList.valueOf(green));remember.setChecked(true);
        remember.setVisibility(r.categoryKey.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);form.addView(remember);
        if(!r.categoryKey.isEmpty()) form.addView(text("Desmarque para alterar apenas este lançamento. A regra usa o estabelecimento ou destinatário original.",12,muted,false));
        if(r.categoryKey.isEmpty()) form.addView(text("Sem estabelecimento ou destinatário identificado, a categoria vale apenas para este lançamento.",12,muted,false));
        com.google.android.material.textfield.TextInputEditText notes = input(form, "Observações (opcional)", r.notes, plain | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); notes.setMinLines(2);
        space(form, 12); LinearLayout original = card(Color.WHITE); form.addView(original);
        original.addView(text("Notificação original", 13, green, true)); space(original, 8); original.addView(text(r.raw, 13, muted, false));
        space(form, 20); button(form, "Salvar alterações", () -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Informe uma descrição"); name.requestFocus(); return; }
            try {
                String entered = value.getText().toString().trim();
                long cents = entered.isEmpty() && r.incomplete ? 0 : new java.math.BigDecimal(entered.replace(',', '.')).movePointRight(2).longValueExact();
                if (cents < 0 || (cents == 0 && !r.incomplete)) throw new ArithmeticException();
                TransactionKind kind = TransactionKind.fromLabel(nature.getText().toString());
                if (type.getText().toString().equals("Receita") && kind != TransactionKind.ACCOUNT_TRANSFER && kind != TransactionKind.UNKNOWN) {
                    nature.setError("Para receitas, escolha Pix / transferência"); nature.requestFocus(); return;
                }
                store.edit(r.id,name.getText().toString().trim(),type.getText().toString().equals("Receita") ? cents : -cents,category.getText().toString(),notes.getText().toString().trim(),kind,counterparty.getText().toString(),remember.isChecked());
                loadEntries(); dialog.dismiss(); render(); Toast.makeText(this, "Alterações salvas", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException | ArithmeticException ex) { value.setError("Use um valor positivo com até 2 casas decimais"); value.requestFocus(); }
        });
        space(form, 8); TextView cancel = text("Voltar sem alterar", 14, green, true); cancel.setGravity(Gravity.CENTER); cancel.setPadding(0, dp(16), 0, dp(16)); form.addView(cancel); cancel.setOnClickListener(v -> dialog.dismiss());
        space(form, 8);
        TextView remove = text(linked.isEmpty() ? "Apagar lançamento" : "Apagar lançamento e fontes associadas", 14, 0xffb0523e, true); remove.setGravity(Gravity.CENTER); remove.setPadding(0, dp(16), 0, dp(16)); form.addView(remove);
        remove.setOnClickListener(v -> {
            store.discard(r.id); loadEntries(); dialog.dismiss(); render();
            com.google.android.material.snackbar.Snackbar.make(root, "Lançamento apagado", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
        });
        dialog.setContentView(scroll);
        dialog.setOnShowListener(d -> {
            android.view.View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(shape(background, 26));
                com.google.android.material.bottomsheet.BottomSheetBehavior<android.view.View> behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
            if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        });
        dialog.show();
    }

    private double categoryTotal(String category) { double sum = 0; for (Entry e : entries) if (e.value < 0 && e.category.startsWith(category + " ·")) sum -= e.value; return sum; }
    private void go(String target) { page = target; render(); }
    private void title(String heading, String subtitle) { content.addView(text(heading, 27, ink, true)); space(content, 6); content.addView(text(subtitle, 14, muted, false)); space(content, 22); }
    private TextView section(String heading, String action) { LinearLayout line = row(); line.addView(text(heading, 17, ink, true), new LinearLayout.LayoutParams(0, -2, 1)); TextView link = text(action, 12, green, true); link.setPadding(dp(8), dp(12), 0, dp(12)); line.addView(link); content.addView(line); space(content, 8); return link; }
    private LinearLayout metric(String label, double amount, int color) { LinearLayout box = card(Color.WHITE); box.addView(text(label, 13, color, true)); space(box, 8); box.addView(text(hidden ? "••••" : money.format(amount), 19, ink, true)); return box; }
    private void transaction(LinearLayout parent, Entry e) { LinearLayout line = row(); line.setPadding(0, dp(12), 0, dp(12)); TextView icon = text(e.record.incomplete ? "?" : e.value > 0 ? "↗" : "↘", 22, e.value > 0 ? green : 0xffbd674e, true); icon.setGravity(Gravity.CENTER); icon.setBackground(shape(e.value > 0 ? 0xffe8f1e8 : 0xfff8eee7, 12)); line.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40))); LinearLayout detail = column(); detail.setPadding(dp(12), 0, dp(8), 0); detail.addView(text(e.name, 14, ink, true)); detail.addView(text(e.record.kind.label + (duplicateIds.contains(e.id) ? " · Possível duplicata" : ""),11,duplicateIds.contains(e.id) ? 0xff9b6b22 : green,true)); detail.addView(text(e.category, 11, muted, false)); if(!e.record.categoryManual && !e.record.category.equals("Outros")) detail.addView(text("Categoria automática",11,green,false)); line.addView(detail, new LinearLayout.LayoutParams(0, -2, 1)); line.addView(text(hidden ? "••••" : (e.record.incomplete ? "Sem valor" : (e.value > 0 ? "+ " : "− ") + money.format(Math.abs(e.value))), 13, e.value > 0 ? green : ink, true)); line.setOnClickListener(v -> review(e)); parent.addView(line); }
    private void category(LinearLayout box, String name, double amount, int color) { LinearLayout line = row(); line.addView(text(name, 14, ink, false), new LinearLayout.LayoutParams(0, -2, 1)); line.addView(text(hidden ? "••••" : money.format(amount), 13, muted, true)); box.addView(line); progress(box, summary.expenses == 0 ? 0 : (int)(amount / (summary.expenses / 100.0) * 100), color); }
    private void budget(LinearLayout box, String name, double spent, double limit) { box.addView(text(name, 15, ink, true)); space(box, 6); box.addView(text(money.format(spent) + " de " + money.format(limit), 13, muted, false)); progress(box, (int)(spent / limit * 100), spent > limit ? 0xffbd674e : green); }
    private void progress(LinearLayout box, int percent, int color) { ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setProgress(Math.min(percent, 100)); bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color)); bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe9eee7)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(8)); lp.setMargins(0, dp(12), 0, dp(18)); box.addView(bar, lp); }
    private void button(LinearLayout box, String label, Runnable action) { TextView button = text(label, 15, Color.WHITE, true); button.setGravity(Gravity.CENTER); button.setPadding(dp(12), dp(17), dp(12), dp(17)); button.setBackground(shape(green, 16)); button.setOnClickListener(v -> action.run()); box.addView(button, new LinearLayout.LayoutParams(-1, -2)); }
    private LinearLayout card(int color) { LinearLayout card = column(); card.setPadding(dp(18), dp(18), dp(18), dp(18)); card.setBackground(shape(color, 22)); return card; }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); t.setLineSpacing(dp(2), 1); return t; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void space(LinearLayout parent, int height) { parent.addView(new Space(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static class Entry {
        final long id; final String name, category; final double value; final CaptureStore.Record record;
        Entry(CaptureStore.Record r) {
            record = r; id = r.id; name = r.name; value = r.cents / 100.0;
            category = (r.incomplete ? "Dados incompletos" : r.category) + " · " + r.source + " · " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, new Locale("pt", "BR")).format(new java.util.Date(r.time));
        }
    }
}
