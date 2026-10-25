package it.example.passolibero;

import java.util.Locale;

/** Calcoli senza dipendenze Android: testabili anche sulla JVM. */
public final class RunMath {
    private RunMath() { }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6_371_000.0;
        double a1 = Math.toRadians(lat1), a2 = Math.toRadians(lat2);
        double dLat = a2 - a1, dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(a1) * Math.cos(a2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    public static String duration(long milliseconds) {
        long sec = Math.max(0, milliseconds) / 1000;
        long h = sec / 3600, m = sec % 3600 / 60, s = sec % 60;
        return h > 0 ? String.format(Locale.ITALY, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.ITALY, "%02d:%02d", m, s);
    }

    public static String pace(long milliseconds, double meters) {
        if (meters < 50 || milliseconds <= 0) return "--:--";
        long secPerKm = Math.round(milliseconds * 1.0 / meters);
        if (secPerKm > 3599) return "--:--";
        return String.format(Locale.ITALY, "%d:%02d", secPerKm / 60, secPerKm % 60);
    }

    public static String km(double meters) {
        return String.format(Locale.ITALY, "%.2f", meters / 1000.0);
    }

    public static String velocity(double kmh) {
        return String.format(Locale.ITALY, "%.1f", Math.max(0, kmh));
    }

    public static double clamp(double x, double lo, double hi) {
        return Math.min(hi, Math.max(lo, x));
    }
}
