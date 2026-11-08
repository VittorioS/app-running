package it.example.passolibero;

/**
 * Avvisi vocali ai progressi del 20, 40, 60 e 80% di un solo sprint.
 * Non stima metri dal tempo: per la distanza accetta esclusivamente la misura
 * della prova GNSS. Le soglie superate tutte insieme NON vengono accodate.
 */
public final class SprintProgressVoicePlan {
    private final SprintTrial.Mode mode;
    private final int target;
    private int announcedMilestone;

    public SprintProgressVoicePlan(SprintTrial.Mode mode, int target) {
        if (mode == null || (mode == SprintTrial.Mode.TIME && (target < 5 || target > 120))
                || (mode == SprintTrial.Mode.DISTANCE && (target < 10 || target > 500)))
            throw new IllegalArgumentException("Obiettivo sprint non valido");
        this.mode = mode;
        this.target = target;
    }

    public int announcedMilestone() { return announcedMilestone; }

    /** Scadenza in tempo monotono del singolo annuncio, solo per sprint a tempo. */
    public long dueAt(long sprintStartElapsedMs, int milestone) {
        if (mode != SprintTrial.Mode.TIME || milestone < 1 || milestone > 4
                || sprintStartElapsedMs < 0)
            throw new IllegalArgumentException("Scadenza temporale non valida");
        return sprintStartElapsedMs + target * 1000L * milestone / 5L;
    }

    public String onElapsedMs(long elapsedMs) {
        if (mode != SprintTrial.Mode.TIME || elapsedMs < 0) return null;
        return update(elapsedMs / 1000.0);
    }

    public String onDistanceM(double meters) {
        if (mode != SprintTrial.Mode.DISTANCE) return null;
        return update(meters);
    }

    private String update(double achieved) {
        if (!Double.isFinite(achieved) || achieved < 0) return null;

        // Il 100% termina lo sprint: non pronunciare checkpoint ormai scaduti.
        if (achieved >= target) {
            announcedMilestone = 4;
            return null;
        }

        int reached = 0;
        for (int i = 1; i <= 4; i++) {
            if (achieved * 5 + 1e-7 >= target * i) reached = i;
        }
        if (reached <= announcedMilestone) return null;

        // In caso di campioni GPS irregolari comunica soltanto il traguardo
        // PIU' RECENTE, mai una raffica di avvisi sul passato.
        announcedMilestone = reached;
        int remaining = (int) Math.round(target * (5 - reached) / 5.0);
        String unit = mode == SprintTrial.Mode.DISTANCE
                ? (remaining == 1 ? "metro" : "metri")
                : (remaining == 1 ? "secondo" : "secondi");
        return "Mancano " + remaining + " " + unit + " alla fine dello sprint";
    }
}
