package it.example.passolibero;

import java.util.Locale;

/** Statistiche e formattazione testabili senza Android. Metri interni, km per l'utente. */
public final class RunStats {
    private RunStats() { }

    public static String instantPace(double speedKmh) {
        if (!Double.isFinite(speedKmh) || speedKmh < 0.8) return "--:--";
        long seconds = Math.round(3600.0 / speedKmh);
        if (seconds > 3599) return "--:--";
        return String.format(Locale.ITALY, "%d:%02d", seconds / 60, seconds % 60);
    }

    public static int nextCheckpointM(double distanceM, int intervalM) {
        if (intervalM <= 0) return 0;
        double nonNegative = Math.max(0, distanceM);
        return (int) Math.min(Integer.MAX_VALUE, (Math.floor(nonNegative / intervalM) + 1) * intervalM);
    }

    public static int remainingCheckpointM(double distanceM, int intervalM) {
        return Math.max(0, (int) Math.ceil(nextCheckpointM(distanceM, intervalM) - distanceM));
    }

    public static String meters(double distanceM) {
        int m = (int) Math.max(0, Math.round(distanceM));
        return m >= 1000 ? RunMath.km(m) + " km" : m + " m";
    }

    public static int goalProgress(double distanceM, int goalM) {
        if (goalM <= 0) return 0;
        return (int) Math.max(0, Math.min(100, Math.round(distanceM / goalM * 100)));
    }

    public static double goalRemaining(double distanceM, int goalM) {
        return Math.max(0, goalM - Math.max(0, distanceM));
    }
}
