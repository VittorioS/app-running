package it.example.passolibero;

/** Classificazione temporale con soglia, isteresi e conferma per 10 secondi.
 * Non conclude né mette in pausa una sessione.
 */
public final class MovementClassifier {
    public enum Kind { UNKNOWN, STILL, WALK, RUN }

    private Kind current = Kind.UNKNOWN;
    private Kind pending = Kind.UNKNOWN;
    private long pendingSinceMs = -1;
    private double speedKmh = Double.NaN;

    public Kind update(long elapsedMs, double measuredKmh, boolean reliable,
                       int recentSteps, long ageLastStepMs, double thresholdKmh) {
        if (!reliable || !Double.isFinite(measuredKmh)) {
            // GPS assente: non inventare una velocità o cambiare stato a caso.
            if (pending != Kind.UNKNOWN) { pending = Kind.UNKNOWN; pendingSinceMs = -1; }
            return current;
        }
        speedKmh = Double.isFinite(speedKmh) ? speedKmh * 0.65 + measuredKmh * 0.35 : measuredKmh;
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
        // Se riceviamo passi recenti, non consideriamo il soggetto fermo.
        if (candidate == Kind.STILL && recentSteps > 0) candidate = Kind.WALK;
        if (candidate == current) {
            pending = Kind.UNKNOWN;
            pendingSinceMs = -1;
            return current;
        }
        if (candidate != pending) {
            pending = candidate;
            pendingSinceMs = elapsedMs;
        } else if (pendingSinceMs >= 0 && elapsedMs - pendingSinceMs >= 10_000) {
            current = candidate;
            pending = Kind.UNKNOWN;
            pendingSinceMs = -1;
        }
        return current;
    }

    public Kind current() { return current; }
    public double smoothedSpeedKmh() { return Double.isFinite(speedKmh) ? speedKmh : 0; }
    public void reset() { current = Kind.UNKNOWN; pending = Kind.UNKNOWN; pendingSinceMs = -1; speedKmh = Double.NaN; }
}
