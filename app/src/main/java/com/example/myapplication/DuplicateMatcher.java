package com.example.myapplication;

/** Indícios de repetição entre fontes; jamais uma autorização para apagar registros. */
public final class DuplicateMatcher {
    public static final long WINDOW_MS = 30 * 60 * 1000L;
    public static String reason(String sourceA, long centsA, long timeA, TransactionKind kindA, String partyA,
                                String sourceB, long centsB, long timeB, TransactionKind kindB, String partyB) {
        boolean aMail = sourceA.startsWith("Gmail"), bMail = sourceB.startsWith("Gmail");
        if (aMail == bMail || centsA >= 0 || centsA != centsB) return null;
        if (kindA != TransactionKind.ACCOUNT_TRANSFER || kindB != TransactionKind.ACCOUNT_TRANSFER) return null;
        if (Math.abs(timeA - timeB) > WINDOW_MS) return null;
        String a = normalize(partyA), b = normalize(partyB);
        if (!a.isEmpty() && !b.isEmpty()) {
            if (!a.equals(b)) return null;
            return "Mesmo valor e destinatário, recebidos em até 30 minutos, por fontes diferentes.";
        }
        return "Mesmo valor e horários próximos em fontes diferentes. Falta o destinatário para comparar.";
    }
    private static String normalize(String value) {
        return NotificationParser.fold(value).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
