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
    private CaptureStore store;
    private long lastRevision = -1;
    private String lastStatus = "";
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (prefs, key) -> runOnUiThread(this::render);
    private final android.os.Handler refreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { public void run() { if (store.revision() != lastRevision || !captureState().equals(lastStatus)) { loadEntries(); render(); } refreshHandler.postDelayed(this, 1500); } };
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new CaptureStore(this);
        if (state != null) {
            page = state.getString("page", "Início");
            filter = state.getString("filter", "Todas");
            hidden = state.getBoolean("hidden");
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
        super.onSaveInstanceState(out); out.putString("page", page); out.putString("filter", filter); out.putBoolean("hidden", hidden);
    }
    private void loadEntries() {
        lastRevision = store.revision();
        entries.clear();
        for (CaptureStore.Record r : store.records(false)) entries.add(new Entry(r));
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
    }

    private void home() {
        title("Seu resumo", "Suas notificações viram organização.");
        captureStatus();
        LinearLayout balance = card(green); content.addView(balance); 
        LinearLayout top = row(); top.addView(text("RESULTADO DOS LANÇAMENTOS", 12, 0xffd3e8dd, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView eye = text(hidden ? "Mostrar" : "Ocultar", 12, Color.WHITE, true); top.addView(eye); eye.setPadding(dp(8), dp(10), dp(8), dp(10)); eye.setOnClickListener(v -> { hidden = !hidden; render(); }); balance.addView(top);
        balance.addView(text(hidden ? "••••••" : money.format(total(true) - total(false)), 34, Color.WHITE, true));
        space(balance, 8); balance.addView(text("Entradas menos saídas · Não é saldo bancário", 12, 0xffd3e8dd, false));
        space(content, 14); LinearLayout metrics = row();
        metrics.addView(metric("↗  Receitas", total(true), green), new LinearLayout.LayoutParams(0, -2, 1));
        Space gap = new Space(this); metrics.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
        metrics.addView(metric("↘  Despesas", total(false), 0xffbd674e), new LinearLayout.LayoutParams(0, -2, 1)); content.addView(metrics);
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
        title("Movimentações", "Notificações capturadas · Toque para editar.");
        LinearLayout tabs = row();
        for (String option : new String[]{"Todas", "Receitas", "Despesas"}) {
            TextView tab = text(option, 13, filter.equals(option) ? Color.WHITE : green, true); tab.setGravity(Gravity.CENTER); tab.setBackground(shape(filter.equals(option) ? green : 0xffe5eee5, 16)); tab.setPadding(dp(8), dp(14), dp(8), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(0, 0, dp(6), 0); tabs.addView(tab, lp);
            tab.setOnClickListener(v -> { filter = option; render(); });
        }
        content.addView(tabs); space(content, 20);
        LinearLayout list = card(Color.WHITE); content.addView(list);
        int count = 0;
        for (Entry e : entries) if (filter.equals("Todas") || (filter.equals("Receitas") && e.value > 0) || (filter.equals("Despesas") && e.value < 0)) { transaction(list, e); count++; }
        if (count == 0) list.addView(text("Nenhuma movimentação por aqui.", 14, muted, false));
        space(content, 20);
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
        space(content, 12); content.addView(text("Lançamentos reconhecidos entram automaticamente. Notificações sem dados suficientes aparecem no extrato como incompletas e ficam fora dos totais até você informar o valor. A captura depende de o Android disponibilizar a notificação; pagamentos e compras podem representar o mesmo gasto, revise antes de somar.", 12, muted, false));
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
        int plain = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        com.google.android.material.textfield.TextInputEditText name = input(form, "Descrição", r.name, plain);
        com.google.android.material.textfield.TextInputEditText value = input(form, "Valor (R$)", r.cents == 0 ? "" : java.math.BigDecimal.valueOf(r.cents).abs().movePointLeft(2).toPlainString().replace('.', ','), android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        com.google.android.material.textfield.MaterialAutoCompleteTextView type = dropdown(form, "Tipo", r.cents > 0 ? "Receita" : "Despesa", new String[]{"Despesa", "Receita"});
        com.google.android.material.textfield.MaterialAutoCompleteTextView category = dropdown(form, "Categoria", r.category, new String[]{"Alimentação", "Casa", "Mobilidade", "Trabalho", "Saúde", "Lazer", "Outros"});
        com.google.android.material.textfield.TextInputEditText notes = input(form, "Observações (opcional)", r.notes, plain | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); notes.setMinLines(2);
        space(form, 12); LinearLayout original = card(Color.WHITE); form.addView(original);
        original.addView(text("Notificação original", 13, green, true)); space(original, 8); original.addView(text(r.raw, 13, muted, false));
        space(form, 20); button(form, "Salvar alterações", () -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Informe uma descrição"); name.requestFocus(); return; }
            try {
                String entered = value.getText().toString().trim();
                long cents = entered.isEmpty() && r.incomplete ? 0 : new java.math.BigDecimal(entered.replace(',', '.')).movePointRight(2).longValueExact();
                if (cents < 0 || (cents == 0 && !r.incomplete)) throw new ArithmeticException();
                store.edit(r.id, name.getText().toString().trim(), type.getText().toString().equals("Receita") ? cents : -cents, category.getText().toString(), notes.getText().toString().trim());
                loadEntries(); dialog.dismiss(); render(); Toast.makeText(this, "Alterações salvas", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException | ArithmeticException ex) { value.setError("Use um valor positivo com até 2 casas decimais"); value.requestFocus(); }
        });
        space(form, 8); TextView cancel = text("Voltar sem alterar", 14, green, true); cancel.setGravity(Gravity.CENTER); cancel.setPadding(0, dp(16), 0, dp(16)); form.addView(cancel); cancel.setOnClickListener(v -> dialog.dismiss());
        space(form, 8);
        TextView remove = text("Apagar lançamento", 14, 0xffb0523e, true); remove.setGravity(Gravity.CENTER); remove.setPadding(0, dp(16), 0, dp(16)); form.addView(remove);
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

    private double total(boolean income) { double sum = 0; for (Entry e : entries) if ((e.value > 0) == income) sum += Math.abs(e.value); return sum; }
    private double categoryTotal(String category) { double sum = 0; for (Entry e : entries) if (e.value < 0 && e.category.startsWith(category + " ·")) sum -= e.value; return sum; }
    private void go(String target) { page = target; render(); }
    private void title(String heading, String subtitle) { content.addView(text(heading, 27, ink, true)); space(content, 6); content.addView(text(subtitle, 14, muted, false)); space(content, 22); }
    private TextView section(String heading, String action) { LinearLayout line = row(); line.addView(text(heading, 17, ink, true), new LinearLayout.LayoutParams(0, -2, 1)); TextView link = text(action, 12, green, true); link.setPadding(dp(8), dp(12), 0, dp(12)); line.addView(link); content.addView(line); space(content, 8); return link; }
    private LinearLayout metric(String label, double amount, int color) { LinearLayout box = card(Color.WHITE); box.addView(text(label, 13, color, true)); space(box, 8); box.addView(text(hidden ? "••••" : money.format(amount), 19, ink, true)); return box; }
    private void transaction(LinearLayout parent, Entry e) { LinearLayout line = row(); line.setPadding(0, dp(12), 0, dp(12)); TextView icon = text(e.record.incomplete ? "?" : e.value > 0 ? "↗" : "↘", 22, e.value > 0 ? green : 0xffbd674e, true); icon.setGravity(Gravity.CENTER); icon.setBackground(shape(e.value > 0 ? 0xffe8f1e8 : 0xfff8eee7, 12)); line.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40))); LinearLayout detail = column(); detail.setPadding(dp(12), 0, dp(8), 0); detail.addView(text(e.name, 14, ink, true)); detail.addView(text(e.category, 11, muted, false)); line.addView(detail, new LinearLayout.LayoutParams(0, -2, 1)); line.addView(text(hidden ? "••••" : (e.record.incomplete ? "Sem valor" : (e.value > 0 ? "+ " : "− ") + money.format(Math.abs(e.value))), 13, e.value > 0 ? green : ink, true)); line.setOnClickListener(v -> review(e)); parent.addView(line); }
    private void category(LinearLayout box, String name, double amount, int color) { LinearLayout line = row(); line.addView(text(name, 14, ink, false), new LinearLayout.LayoutParams(0, -2, 1)); line.addView(text(hidden ? "••••" : money.format(amount), 13, muted, true)); box.addView(line); progress(box, total(false) == 0 ? 0 : (int)(amount / total(false) * 100), color); }
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
