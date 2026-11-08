package it.example.passolibero;

/**
 * Regole di evidenziazione pure (senza Android) e testabili su JVM.
 * Zero = nessuna selezione: tutte le andature mantengono il proprio colore.
 */
public final class RouteSectionSelection {
    private static final int WALK = 0xFF1E9954;
    private static final int RUN = 0xFF2863E0;
    private static final int SPRINT = 0xFFDE3540;
    private static final int OTHER = 0xFF7F858D;
    private static final int DIMMED = 0xFFB0B6BE;

    private RouteSectionSelection() { }

    public static int color(RouteSections.Kind kind, int number, int selectedNumber) {
        if (selectedNumber != 0 && selectedNumber != number) return DIMMED;
        switch (kind) {
            case WALK: return WALK;
            case RUN: return RUN;
            case SPRINT: return SPRINT;
            default: return OTHER;
        }
    }

    public static int widthDp(int number, int selectedNumber) {
        return number == selectedNumber && selectedNumber != 0 ? 10 : 7;
    }

    /**
     * Indici zero-based nell'ordine in cui osmdroid deve disegnare le linee.
     * Nessuna selezione: ordine cronologico; selezione: tratto scelto per ultimo.
     */
    public static int[] drawOrder(int count, int selectedNumber) {
        if (count < 0 || selectedNumber < 0 || selectedNumber > count)
            throw new IllegalArgumentException("Selezione fuori intervallo");
        int[] order = new int[count];
        int out = 0;
        for (int i = 0; i < count; i++) {
            if (i + 1 != selectedNumber) order[out++] = i;
        }
        if (selectedNumber != 0) order[out] = selectedNumber - 1;
        return order;
    }
}
