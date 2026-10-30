package it.example.passolibero;

/**
 * Corrobora il movimento con passi, velocita' GNSS e spostamenti persistenti.
 * L'accuratezza GPS autorizza l'uso di un fix, NON dimostra il movimento.
 * Indipendente da Android per poter simulare deriva e avvio della corsa nei test.
 */
public final class MotionEvidencePolicy {
    private static final long GEOMETRY_WINDOW_MS = 8_000L;
    private static final long GPS_SPEED_CONFIRM_MS = 2_000L;
    private static final long MOTION_GRACE_MS = 6_000L;
    private long windowStartedMs = -1L;
    private double windowLat, windowLon, windowAccuracy;
    private int consecutiveDisplacements;
    private long geometricEvidenceUntilMs = -1L;
    private long nativeSpeedSinceMs = -1L;
    private long lastMotionEvidenceMs = -1L;
    private boolean moving;

    /**
     * @param recentSteps numero di passi negli ultimi ~5 secondi
     * @param stepAgeMs eta' dell'ultimo passo, Long.MAX_VALUE se mai osservato
     * @param stepSensorAvailable vero SOLO se il listener e' registrato
     * @param nativeSpeedMps velocita' fornita da Location, NaN se assente
     * @param speedUncertaintyMps incertezza della velocita', NaN se assente
     */
    public boolean update(long nowMs, double lat, double lon, double accuracyM,
                          int recentSteps, long stepAgeMs, boolean stepSensorAvailable,
                          double nativeSpeedMps, double speedUncertaintyMps) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon)
                || !Double.isFinite(accuracyM) || accuracyM <= 0) return moving;

        boolean stepsConfirm = stepSensorAvailable && recentSteps >= 2 && stepAgeMs <= 6_000L;
        boolean validNative = Double.isFinite(nativeSpeedMps)
                && nativeSpeedMps >= 0 && nativeSpeedMps <= 12
                && Double.isFinite(speedUncertaintyMps) && speedUncertaintyMps >= 0;
        // La sola velocita' riportata senza incertezza non basta per sbloccare il GPS.
        boolean nativeMoving = validNative && nativeSpeedMps - speedUncertaintyMps >= 0.9;
        boolean nativeStill = validNative && nativeSpeedMps + speedUncertaintyMps < 0.7;
        if (nativeMoving) {
            if (nativeSpeedSinceMs < 0) nativeSpeedSinceMs = nowMs;
        } else {
            nativeSpeedSinceMs = -1L;
        }
        boolean confirmedNative = nativeSpeedSinceMs >= 0
                && nowMs - nativeSpeedSinceMs >= GPS_SPEED_CONFIRM_MS;

        if (windowStartedMs < 0 || nowMs < windowStartedMs) {
            setWindow(nowMs, lat, lon, accuracyM);
        } else if (nowMs - windowStartedMs >= GEOMETRY_WINDOW_MS) {
            double travelled = RunMath.distanceMeters(windowLat, windowLon, lat, lon);
            double uncertainty = Math.max(windowAccuracy, accuracyM);
            // Se mancano i passi, chiediamo un movimento maggiore del rumore dichiarato.
            double minTravel = Math.max(12.0, uncertainty * (stepSensorAvailable ? 2.5 : 2.0));
            boolean significant = !nativeStill && travelled > minTravel;
            consecutiveDisplacements = significant ? consecutiveDisplacements + 1 : 0;
            // In assenza di passi, due finestre coerenti prima di parlare di movimento.
            if (consecutiveDisplacements >= (stepSensorAvailable ? 2 : 1)) {
                geometricEvidenceUntilMs = nowMs + GEOMETRY_WINDOW_MS + 2_000L;
            }
            setWindow(nowMs, lat, lon, accuracyM);
        }

        boolean corroborated = stepsConfirm || confirmedNative
                || (geometricEvidenceUntilMs >= nowMs);
        if (corroborated) {
            lastMotionEvidenceMs = nowMs;
            moving = true;
        } else if (moving && (lastMotionEvidenceMs < 0
                || nowMs - lastMotionEvidenceMs >= MOTION_GRACE_MS)) {
            moving = false;
        }
        return moving;
    }

    public boolean moving() { return moving; }

    public void reset() {
        windowStartedMs = -1L;
        consecutiveDisplacements = 0;
        geometricEvidenceUntilMs = -1L;
        nativeSpeedSinceMs = -1L;
        lastMotionEvidenceMs = -1L;
        moving = false;
    }

    private void setWindow(long ms, double lat, double lon, double accuracyM) {
        windowStartedMs = ms;
        windowLat = lat;
        windowLon = lon;
        windowAccuracy = accuracyM;
    }
}
