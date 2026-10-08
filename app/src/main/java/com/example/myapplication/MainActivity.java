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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
        return NubankNotificationService.connected ? "Captura ativa · Nubank" : "Acesso autorizado · Aguardando conexão";
    }
    private void openPermission() {
        new MaterialAlertDialogBuilder(this).setTitle("Autorizar leitura de notificações")
            .setMessage("O Android concede acesso às notificações do aparelho. O Finna filtra apenas o Nubank e armazena o texto localmente para organizar e revisar lançamentos. Nenhum dado é enviado ao banco ou a servidores. Na próxima tela, habilite o Finna. Você pode revogar o acesso a qualquer momento.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Abrir configurações", (d, w) -> {
                try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                catch (android.content.ActivityNotFoundException ex) { Toast.makeText(this, "Configuração indisponível neste aparelho", Toast.LENGTH_LONG).show(); }
            }).show();
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
        title("Movimentações", "Notificações capturadas · Toque para revisar.");
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
        space(content, 20); pendingNotifications();
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
        status.addView(text(store.records(true).size() + " notificações aguardam revisão", 12, muted, false));
        space(status, 8); button(status, "Configurar captura", () -> go("Captura")); space(content, 18);
    }
    private void profile() {
        title("Captura automática", "Leitura local das notificações do Nubank.");
        captureStatus();
        LinearLayout details = card(Color.WHITE); content.addView(details);
        details.addView(text("Como funciona", 19, ink, true)); space(details, 12);
        details.addView(text("Autorize o Finna nas configurações do Android. Novas notificações de compras, Pix e boletos são analisadas automaticamente, mesmo com a tela do app fechada.\n\nNão há acesso à conta, senha ou conexão com o banco. Os lançamentos ficam salvos neste aparelho. O histórico anterior à autorização não é importado.", 14, ink, false));
        space(details, 16); button(details, "Gerenciar acesso no Android", this::openPermission);
        space(details, 12);
        boolean paused = getSharedPreferences("capture", MODE_PRIVATE).getBoolean("paused", false);
        button(details, paused ? "Retomar captura" : "Pausar captura", () -> {
            getSharedPreferences("capture", MODE_PRIVATE).edit().putBoolean("paused", !paused).apply();
        });
        space(content, 20); pendingNotifications();
        space(content, 12); content.addView(text("Compras recusadas, conteúdo oculto e formatos desconhecidos exigem revisão. A captura depende de o Android disponibilizar a notificação; pagamentos e compras podem representar o mesmo gasto, revise antes de somar.", 12, muted, false));
    }
    private void pendingNotifications() {
        section("Aguardando revisão", "Nubank");
        java.util.ArrayList<CaptureStore.Record> pending = store.records(true);
        if (pending.isEmpty()) { content.addView(text("Nenhuma notificação pendente.", 14, muted, false)); return; }
        for (CaptureStore.Record r : pending) {
            LinearLayout box = card(Color.WHITE); content.addView(box);
            box.addView(text(r.raw, 14, ink, false)); space(box, 12);
            button(box, "Revisar lançamento", () -> resolve(r)); space(content, 12);
        }
    }
    private void resolve(CaptureStore.Record r) {
        LinearLayout form = column(); form.setPadding(dp(24), dp(8), dp(24), 0);
        EditText name = new EditText(this); name.setHint("Descrição"); form.addView(name);
        EditText value = new EditText(this); value.setHint("Valor em reais (ex.: 39,90)"); value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL); form.addView(value);
        Spinner type = new Spinner(this); type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Despesa", "Receita"})); form.addView(type);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Revisar notificação").setView(form)
            .setNegativeButton("Cancelar", null).setNeutralButton("Ignorar", (d, w) -> { store.discard(r.id); render(); }).setPositiveButton("Salvar", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Informe a descrição"); return; }
            try {
                long cents = new java.math.BigDecimal(value.getText().toString().replace(',', '.')).movePointRight(2).longValueExact();
                if (cents <= 0) throw new ArithmeticException();
                store.resolve(r.id, name.getText().toString().trim(), type.getSelectedItemPosition() == 0 ? -cents : cents, "Outros");
                loadEntries(); dialog.dismiss(); render();
            } catch (NumberFormatException | ArithmeticException ex) { value.setError("Informe um valor positivo com até 2 casas decimais"); }
        })); dialog.show();
    }

    private void review(Entry entry) {
        String[] categories = {"Alimentação", "Casa", "Mobilidade", "Trabalho", "Outros"};
        new MaterialAlertDialogBuilder(this).setTitle("Revisar categoria")
            .setItems(categories, (d, which) -> {
                int index = entries.indexOf(entry);
                if (index >= 0) store.categorize(entry.id, categories[which]);
                loadEntries();
                render();
            }).setNegativeButton("Cancelar", null).show();
    }

    private double total(boolean income) { double sum = 0; for (Entry e : entries) if ((e.value > 0) == income) sum += Math.abs(e.value); return sum; }
    private double categoryTotal(String category) { double sum = 0; for (Entry e : entries) if (e.value < 0 && e.category.startsWith(category + " ·")) sum -= e.value; return sum; }
    private void go(String target) { page = target; render(); }
    private void title(String heading, String subtitle) { content.addView(text(heading, 27, ink, true)); space(content, 6); content.addView(text(subtitle, 14, muted, false)); space(content, 22); }
    private TextView section(String heading, String action) { LinearLayout line = row(); line.addView(text(heading, 17, ink, true), new LinearLayout.LayoutParams(0, -2, 1)); TextView link = text(action, 12, green, true); link.setPadding(dp(8), dp(12), 0, dp(12)); line.addView(link); content.addView(line); space(content, 8); return link; }
    private LinearLayout metric(String label, double amount, int color) { LinearLayout box = card(Color.WHITE); box.addView(text(label, 13, color, true)); space(box, 8); box.addView(text(hidden ? "••••" : money.format(amount), 19, ink, true)); return box; }
    private void transaction(LinearLayout parent, Entry e) { LinearLayout line = row(); line.setPadding(0, dp(12), 0, dp(12)); TextView icon = text(e.value > 0 ? "↗" : "↘", 22, e.value > 0 ? green : 0xffbd674e, true); icon.setGravity(Gravity.CENTER); icon.setBackground(shape(e.value > 0 ? 0xffe8f1e8 : 0xfff8eee7, 12)); line.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40))); LinearLayout detail = column(); detail.setPadding(dp(12), 0, dp(8), 0); detail.addView(text(e.name, 14, ink, true)); detail.addView(text(e.category, 11, muted, false)); line.addView(detail, new LinearLayout.LayoutParams(0, -2, 1)); line.addView(text(hidden ? "••••" : (e.value > 0 ? "+ " : "− ") + money.format(Math.abs(e.value)), 13, e.value > 0 ? green : ink, true)); line.setOnClickListener(v -> review(e)); parent.addView(line); }
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
        final long id; final String name, category; final double value;
        Entry(CaptureStore.Record r) {
            id = r.id; name = r.name; value = r.cents / 100.0;
            category = r.category + " · " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, new Locale("pt", "BR")).format(new java.util.Date(r.time));
        }
    }
}
