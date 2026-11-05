package it.example.passolibero;

/** Proiezioni UI: non interviene sul rilevamento RUN/WALK e non stima dati mancanti. */
public final class SessionTabStats {
    public enum Tab { RUN, WALK, TOTAL }

    private SessionTabStats() { }

    public static double meters(Tab tab, double runM, double walkM) {
        if (tab == Tab.RUN) return Math.max(0, runM);
        if (tab == Tab.WALK) return Math.max(0, walkM);
        return Math.max(0, runM) + Math.max(0, walkM);
    }

    public static long milliseconds(Tab tab, long runMs, long walkMs) {
        if (tab == Tab.RUN) return Math.max(0, runMs);
        if (tab == Tab.WALK) return Math.max(0, walkMs);
        return Math.max(0, runMs) + Math.max(0, walkMs);
    }

    public static int steps(Tab tab, int runSteps, int walkSteps) {
        if (tab == Tab.RUN) return Math.max(0, runSteps);
        if (tab == Tab.WALK) return Math.max(0, walkSteps);
        return Math.max(0, runSteps) + Math.max(0, walkSteps);
    }

    /** Nessun dato istantaneo di una modalita' se l'utente ora ne pratica un'altra. */
    public static boolean hasCurrentSpeed(Tab tab, String movement) {
        if (tab == Tab.RUN) return "RUN".equals(movement);
        if (tab == Tab.WALK) return "WALK".equals(movement);
        return "RUN".equals(movement) || "WALK".equals(movement);
    }

    public static double averageSpeedKmh(long milliseconds, double meters) {
        if (milliseconds <= 0 || !Double.isFinite(meters) || meters <= 0) return Double.NaN;
        return meters * 3600.0 / milliseconds;
    }

    public static double averageCadence(long milliseconds, int steps) {
        if (milliseconds <= 0 || steps <= 0) return Double.NaN;
        return steps * 60_000.0 / milliseconds;
    }
}
