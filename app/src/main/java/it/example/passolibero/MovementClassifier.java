package it.example.passolibero;

/**
 * Riconoscimento del movimento in tempo quasi reale.
 *
 * Le coordinate GPS vengono richieste dal servizio circa ogni secondo: qui
 * filtriamo la velocita', NON rallentiamo la frequenza delle letture GPS.
 * Il filtro tiene conto del tempo reale fra fix, cosi' si adatta se Android
 * consegna alcune posizioni in ritardo.
 */
public final class MovementClassifier {
    public enum Kind { UNKNOWN, STILL, WALK, RUN }

    // Circa 84% del nuovo campione con fix a 1 Hz; 10 -> 4 km/h si aggiorna
    // gia' al primo fix, mentre isteresi e conferma evitano oscillazioni.
    private static final double SPEED_TIME_CONSTANT_SEC = 0.55;
    private static final long MOTION_CONFIRM_MS = 2_000L;
    private static final long STILL_CONFIRM_MS = 4_000L;

    private Kind current = Kind.UNKNOWN;
    private Kind pending = Kind.UNKNOWN;
    private long pendingSinceMs = -1;
    private long lastMeasuredMs = -1;
    private double speedKmh = Double.NaN;

    public Kind update(long elapsedMs, double measuredKmh, boolean reliable,
                       int recentSteps, long ageLastStepMs, double thresholdKmh) {
        if (!reliable || !Double.isFinite(measuredKmh) || measuredKmh < 0) {
            // GPS assente: niente variazioni inventate e nessuna conferma pendente.
            pending = Kind.UNKNOWN;
            pendingSinceMs = -1;
            return current;
        }

        double dtSeconds = lastMeasuredMs < 0 ? 1.0
                : Math.max(0.05, Math.min(5.0, (elapsedMs - lastMeasuredMs) / 1000.0));
        // Un fix fuori ordine non deve trascinare la velocita' o anticipare stati.
        if (lastMeasuredMs >= 0 && elapsedMs <= lastMeasuredMs) return current;
        lastMeasuredMs = elapsedMs;
        double alpha = 1.0 - Math.exp(-dtSeconds / SPEED_TIME_CONSTANT_SEC);
        speedKmh = Double.isFinite(speedKmh)
                ? speedKmh + alpha * (measuredKmh - speedKmh) : measuredKmh;

        Kind candidate;
        if (speedKmh < 0.8 && ageLastStepMs > 8_000) {
            candidate = Kind.STILL;
        } else if (current == Kind.RUN) {
            candidate = speedKmh < thresholdKmh - 0.5 ? Kind.WALK : Kind.RUN;
        } else if (current == Kind.WALK) {
            candidate = speedKmh > thresholdKmh + 0.5 ? Kind.RUN : Kind.WALK;
        } else {
            candidate = speedKmh >= thresholdKmh ? Kind.RUN : Kind.WALK;
        }
        if (candidate == Kind.STILL && recentSteps > 0) candidate = Kind.WALK;

        if (candidate == current) {
            pending = Kind.UNKNOWN;
            pendingSinceMs = -1;
            return current;
        }
        if (candidate != pending) {
            pending = candidate;
            pendingSinceMs = elapsedMs;
        } else if (pendingSinceMs >= 0
                && elapsedMs - pendingSinceMs >= (candidate == Kind.STILL
                    ? STILL_CONFIRM_MS : MOTION_CONFIRM_MS)) {
            current = candidate;
            pending = Kind.UNKNOWN;
            pendingSinceMs = -1;
        }
        return current;
    }

    public Kind current() { return current; }
    public double smoothedSpeedKmh() { return Double.isFinite(speedKmh) ? speedKmh : 0; }
    public void reset() {
        current = Kind.UNKNOWN;
        pending = Kind.UNKNOWN;
        pendingSinceMs = -1;
        lastMeasuredMs = -1;
        speedKmh = Double.NaN;
    }
}
