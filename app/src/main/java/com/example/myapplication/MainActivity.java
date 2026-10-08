package com.example.myapplication;

import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

/** Frontend demonstrativo: os lançamentos existem somente durante esta sessão. */
public class MainActivity extends AppCompatActivity {
    private final int ink = Color.rgb(26, 45, 40), green = Color.rgb(26, 112, 83);
    private final int muted = Color.rgb(114, 128, 121), background = Color.rgb(246, 248, 245);
    private LinearLayout root, content, navigation;
    private String page = "Início", filter = "Todas";
    private boolean hidden = false;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            page = state.getString("page", "Início");
            hidden = state.getBoolean("hidden");
            filter = state.getString("filter", "Todas");
            String[] names = state.getStringArray("names");
            String[] categories = state.getStringArray("categories");
            double[] values = state.getDoubleArray("values");
            if (names != null && categories != null && values != null)
                for (int i = 0; i < names.length; i++) entries.add(new Entry(names[i], categories[i], values[i]));
        } else {
            entries.add(new Entry("Salário", "Trabalho · Hoje", 5200));
            entries.add(new Entry("Supermercado", "Alimentação · Hoje", -245.90));
            entries.add(new Entry("Internet", "Casa · Ontem", -99.90));
            entries.add(new Entry("Café da tarde", "Alimentação · Ontem", -18.50));
            entries.add(new Entry("Transporte", "Mobilidade · Ontem", -42));
        }
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("page", page); out.putString("filter", filter); out.putBoolean("hidden", hidden);
        String[] names = new String[entries.size()], categories = new String[entries.size()];
        double[] values = new double[entries.size()];
        for (int i = 0; i < entries.size(); i++) { Entry e = entries.get(i); names[i] = e.name; categories[i] = e.category; values[i] = e.value; }
        out.putStringArray("names", names); out.putStringArray("categories", categories); out.putDoubleArray("values", values);
    }

    private void render() {
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
        header.addView(avatar, new LinearLayout.LayoutParams(dp(46), dp(46))); avatar.setContentDescription("Abrir perfil"); avatar.setOnClickListener(v -> go("Perfil"));
        content.addView(header); space(content, 28);
        if (page.equals("Início")) home();
        else if (page.equals("Extrato")) statement();
        else if (page.equals("Planejar")) planning();
        else profile();
        navigation = row(); navigation.setPadding(dp(8), dp(10), dp(8), dp(10)); navigation.setBackgroundColor(Color.WHITE);
        String[] labels = {"Início", "Extrato", "Planejar", "Perfil"};
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
        title("Seu resumo", "Um passo de cada vez, rumo ao equilíbrio.");
        LinearLayout balance = card(green); content.addView(balance); 
        LinearLayout top = row(); top.addView(text("SALDO DISPONÍVEL", 12, 0xffd3e8dd, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView eye = text(hidden ? "Mostrar" : "Ocultar", 12, Color.WHITE, true); top.addView(eye); eye.setPadding(dp(8), dp(10), dp(8), dp(10)); eye.setOnClickListener(v -> { hidden = !hidden; render(); }); balance.addView(top);
        balance.addView(text(hidden ? "••••••" : money.format(total(true) - total(false)), 34, Color.WHITE, true));
        space(balance, 8); balance.addView(text("Conta principal · Dados de exemplo", 12, 0xffd3e8dd, false));
        space(content, 14); LinearLayout metrics = row();
        metrics.addView(metric("↗  Receitas", total(true), green), new LinearLayout.LayoutParams(0, -2, 1));
        Space gap = new Space(this); metrics.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
        metrics.addView(metric("↘  Despesas", total(false), 0xffbd674e), new LinearLayout.LayoutParams(0, -2, 1)); content.addView(metrics);
        space(content, 18); button(content, "+  Novo lançamento", this::newEntry);
        space(content, 24); section("Para onde vai seu dinheiro", "Este mês");
        LinearLayout expenses = card(Color.WHITE); content.addView(expenses);
        category(expenses, "Alimentação", categoryTotal("Alimentação"), 0xffd5a24c);
        category(expenses, "Casa", categoryTotal("Casa"), 0xff7d9e8c);
        category(expenses, "Mobilidade", categoryTotal("Mobilidade"), 0xff8799b5);
        space(content, 24); section("Últimas movimentações", "Ver todas").setOnClickListener(v -> go("Extrato"));
        LinearLayout transactions = card(Color.WHITE); content.addView(transactions);
        for (int i = 0; i < Math.min(3, entries.size()); i++) transaction(transactions, entries.get(i));
    }

    private void statement() {
        title("Movimentações", "Acompanhe suas entradas e saídas.");
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
        space(content, 20); button(content, "+  Novo lançamento", this::newEntry);
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

    private void profile() {
        title("Seu perfil", "Tudo pronto para começar a se organizar.");
        LinearLayout profile = card(Color.WHITE); content.addView(profile);
        profile.addView(text("Olá, visitante!", 22, ink, true)); space(profile, 10);
        profile.addView(text("Esta é uma prévia do seu gestor financeiro pessoal.", 15, muted, false));
        space(content, 20); LinearLayout about = card(Color.WHITE); content.addView(about);
        about.addView(text("Sobre o finna", 18, ink, true)); space(about, 12);
        about.addView(text("Versão 1.0 · Protótipo de interface\n\nOs valores são demonstrativos. Novos lançamentos ficam apenas na sessão atual.\n\nLogin, contas e sincronização poderão ser conectados na próxima etapa.", 14, muted, false));
    }

    private void newEntry() {
        LinearLayout form = column(); form.setPadding(dp(24), dp(8), dp(24), 0);
        EditText name = new EditText(this); name.setHint("Descrição"); name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); form.addView(name);
        EditText value = new EditText(this); value.setHint("Valor (R$)"); value.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); form.addView(value);
        Spinner type = new Spinner(this); type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Despesa", "Receita"})); form.addView(type);
        Spinner category = new Spinner(this); category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Alimentação", "Casa", "Mobilidade", "Trabalho", "Outros"})); form.addView(category);
        TextView note = text("Demonstração: o lançamento não será salvo em banco de dados.", 12, muted, false); note.setPadding(0, dp(12), 0, dp(12)); form.addView(note);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Novo lançamento").setView(form).setNegativeButton("Cancelar", null).setPositiveButton("Adicionar", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Informe uma descrição"); return; }
            double amount;
            try { amount = Double.parseDouble(value.getText().toString().replace(',', '.')); } catch (NumberFormatException ex) { value.setError("Informe um valor válido"); return; }
            if (amount <= 0 || Double.isInfinite(amount) || Double.isNaN(amount)) { value.setError("Use um valor maior que zero"); return; }
            entries.add(0, new Entry(name.getText().toString().trim(), category.getSelectedItem() + " · Agora", type.getSelectedItemPosition() == 0 ? -amount : amount));
            dialog.dismiss(); render(); Toast.makeText(this, "Lançamento adicionado à demonstração", Toast.LENGTH_SHORT).show();
        })); dialog.show();
    }

    private double total(boolean income) { double sum = 0; for (Entry e : entries) if ((e.value > 0) == income) sum += Math.abs(e.value); return sum; }
    private double categoryTotal(String category) { double sum = 0; for (Entry e : entries) if (e.value < 0 && e.category.startsWith(category + " ·")) sum -= e.value; return sum; }
    private void go(String target) { page = target; render(); }
    private void title(String heading, String subtitle) { content.addView(text(heading, 27, ink, true)); space(content, 6); content.addView(text(subtitle, 14, muted, false)); space(content, 22); }
    private TextView section(String heading, String action) { LinearLayout line = row(); line.addView(text(heading, 17, ink, true), new LinearLayout.LayoutParams(0, -2, 1)); TextView link = text(action, 12, green, true); link.setPadding(dp(8), dp(12), 0, dp(12)); line.addView(link); content.addView(line); space(content, 8); return link; }
    private LinearLayout metric(String label, double amount, int color) { LinearLayout box = card(Color.WHITE); box.addView(text(label, 13, color, true)); space(box, 8); box.addView(text(hidden ? "••••" : money.format(amount), 19, ink, true)); return box; }
    private void transaction(LinearLayout parent, Entry e) { LinearLayout line = row(); line.setPadding(0, dp(12), 0, dp(12)); TextView icon = text(e.value > 0 ? "↗" : "↘", 22, e.value > 0 ? green : 0xffbd674e, true); icon.setGravity(Gravity.CENTER); icon.setBackground(shape(e.value > 0 ? 0xffe8f1e8 : 0xfff8eee7, 12)); line.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40))); LinearLayout detail = column(); detail.setPadding(dp(12), 0, dp(8), 0); detail.addView(text(e.name, 14, ink, true)); detail.addView(text(e.category, 11, muted, false)); line.addView(detail, new LinearLayout.LayoutParams(0, -2, 1)); line.addView(text(hidden ? "••••" : (e.value > 0 ? "+ " : "− ") + money.format(Math.abs(e.value)), 13, e.value > 0 ? green : ink, true)); parent.addView(line); }
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
    private static class Entry { final String name, category; final double value; Entry(String name, String category, double value) { this.name = name; this.category = category; this.value = value; } }
}
