package it.example.passolibero;

/** Aggregazione statistica passiva: non altera la classificazione del movimento. */
public final class RunPeakSpeedPolicy {
    private RunPeakSpeedPolicy() { }

    /**
     * Accetta soltanto velocita' di tratti di corsa gia' classificati dal sistema,
     * in presenza di movimento corroborato e GPS recente/accurato.
     *
     * @param currentMax massimo esistente; NaN se nessuna misura valida
     * @param movement codice corrente del classificatore (RUN, WALK, ecc.)
     * @param moving evidenza indipendente di movimento
     * @param fresh fix GPS recente e adatto al classificatore
     * @param accuracyM incertezza posizione (metri)
     * @param acceptedMeters distanza reale ammessa dagli attuali filtri GPS
     * @param speedKmh velocita' corrente gia' validata dal TrackingService
     */
    public static double update(double currentMax, String movement, boolean moving,
                                boolean fresh, double accuracyM, double acceptedMeters,
                                double speedKmh) {
        if (!"RUN".equals(movement) || !moving || !fresh
                || !Double.isFinite(accuracyM) || accuracyM < 0 || accuracyM > 15
                || !Double.isFinite(acceptedMeters) || acceptedMeters <= 0
                || !Double.isFinite(speedKmh) || speedKmh <= 0 || speedKmh > 40)
            return currentMax;
        if (!Double.isFinite(currentMax) || currentMax <= 0) return speedKmh;
        return Math.max(currentMax, speedKmh);
    }
}
