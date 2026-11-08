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
}
