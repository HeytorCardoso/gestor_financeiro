package com.example.myapplication;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Apenas formatos explícitos; mensagens ambíguas exigem revisão. */
public final class NotificationParser {
    private static final String AMOUNT = "R\\$\\s*((?:\\d{1,3}(?:\\.\\d{3})+|\\d+),\\d{2})";
    private static final Pattern[] FORMATS = {
        Pattern.compile("Compra de " + AMOUNT + " aprovada em (.+?) às \\d{2}:\\d{2}\\.?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("Você enviou " + AMOUNT + " para (.+?) via Pix\\.?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("Você recebeu " + AMOUNT + " de (.+?) via Pix\\.?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("O pagamento do seu boleto no valor de " + AMOUNT + " foi realizado com sucesso\\.?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    };
    public static Result parse(String text) {
        String normalized = text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        Matcher amounts = Pattern.compile(AMOUNT).matcher(normalized);
        int amountCount = 0;
        while (amounts.find()) amountCount++;
        if (amountCount != 1) return null;
        for (int i = 0; i < FORMATS.length; i++) {
            Matcher m = FORMATS[i].matcher(normalized);
            if (!m.matches()) continue;
            try {
                long cents = Long.parseLong(m.group(1).replace(".", "").replace(",", ""));
                if (cents <= 0) return null;
                String name = i == 3 ? "Pagamento de boleto" : m.group(2);
                if (i == 1) name = "Pix para " + name;
                if (i == 2) name = "Pix de " + name;
                return new Result(name, i == 2 ? cents : -cents);
            } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }
    public static final class Result {
        public final String name;
        public final long cents;
        Result(String name, long cents) { this.name = name; this.cents = cents; }
    }
}
