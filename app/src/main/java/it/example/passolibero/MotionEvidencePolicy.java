package it.example.passolibero;

/**
 * Interprets independent, timestamped evidence; absence of motion is NOT stillness.
 * Pure Java so recorded GNSS/sensor traces can be replayed in unit tests.
 */
public final class MotionEvidencePolicy {
    public enum Evidence { MOVING, STILL_STRONG, STILL_MODERATE, UNCERTAIN }
    public static final long GPS_FRESH_MS = 2_500L;
    public static final double STILL_UPPER_MPS = 0.35;
    public static final double MOVING_LOWER_MPS = 0.70;
    private static final long GEOMETRY_WINDOW_MS = 6_000L;
    private long lastFixMs = -1L;
    private long geometryFromMs = -1L;
    private double geometryLat, geometryLon, geometryAccuracy;
    private long geometricMovingUntil = -1L;
    private int consecutiveSlowFixes, consecutiveFastFixes;
    private boolean lastGnssStill, lastGnssMoving;

    /**
     * Only call for new, accepted, monotonic GNSS fixes, never once per timer tick.
     * Horizontal accuracy gates a fix; it does not prove the user is moving.
     */
    public void observeGps(long fixMs, double lat, double lon, double horizontalAccuracyM,
                           double nativeSpeedMps, double speedAccuracyMps) {
        if (fixMs <= lastFixMs || !Double.isFinite(horizontalAccuracyM)
                || horizontalAccuracyM <= 0 || horizontalAccuracyM > 25
                || !Double.isFinite(lat) || !Double.isFinite(lon)) return;
        if (lastFixMs < 0 || fixMs - lastFixMs > GPS_FRESH_MS * 2) {
            consecutiveSlowFixes = 0;
            consecutiveFastFixes = 0;
            geometryFromMs = -1;
            geometricMovingUntil = -1;
        }
        lastFixMs = fixMs;
        boolean usableSpeed = Double.isFinite(nativeSpeedMps)
                && nativeSpeedMps >= 0 && nativeSpeedMps <= 12
                && Double.isFinite(speedAccuracyMps) && speedAccuracyMps >= 0;
        lastGnssStill = usableSpeed && nativeSpeedMps + speedAccuracyMps < STILL_UPPER_MPS;
        lastGnssMoving = usableSpeed && nativeSpeedMps - speedAccuracyMps > MOVING_LOWER_MPS;
        consecutiveSlowFixes = lastGnssStill ? consecutiveSlowFixes + 1 : 0;
        consecutiveFastFixes = lastGnssMoving ? consecutiveFastFixes + 1 : 0;

        if (geometryFromMs < 0) {
            geometryFromMs = fixMs;
            geometryLat = lat;
            geometryLon = lon;
            geometryAccuracy = horizontalAccuracyM;
        } else if (fixMs - geometryFromMs >= GEOMETRY_WINDOW_MS) {
            double meters = RunMath.distanceMeters(geometryLat, geometryLon, lat, lon);
            double uncertainty = Math.hypot(geometryAccuracy, horizontalAccuracyM);
            // Significant displacement proves movement; overlap does NOT prove stillness.
            if (!lastGnssStill && meters > Math.max(6.0, uncertainty * 1.3)) {
                geometricMovingUntil = fixMs + 2_500L;
                geometryFromMs = fixMs;
                geometryLat = lat;
                geometryLon = lon;
                geometryAccuracy = horizontalAccuracyM;
            } else if (fixMs - geometryFromMs >= 20_000L) {
                // Keep accumulating slow displacement across multiple windows.
                geometryFromMs = fixMs;
                geometryLat = lat;
                geometryLon = lon;
                geometryAccuracy = horizontalAccuracyM;
            }
        }
    }

    /**
     * STEP_COUNTER progress may indicate motion, but its absence never proves a stop.
     * stepDetectorPreviouslyActive is true only after real detector events this session.
     */
    public Evidence evaluate(long nowMs, int detectorStepsLastFiveSeconds,
                             long lastDetectorStepAgeMs, boolean stepDetectorAvailable,
                             boolean stepDetectorPreviouslyActive, boolean counterProgressRecent,
                             boolean inertialGait) {
        boolean gnssFresh = lastFixMs >= 0 && nowMs >= lastFixMs
                && nowMs - lastFixMs <= GPS_FRESH_MS;
        boolean detectorWalking = stepDetectorAvailable && detectorStepsLastFiveSeconds >= 2
                && lastDetectorStepAgeMs >= 0 && lastDetectorStepAgeMs <= 2_000;
        if (detectorWalking || counterProgressRecent || inertialGait
                || (gnssFresh && consecutiveFastFixes >= 2)
                || (geometricMovingUntil >= nowMs && gnssFresh)) {
            return Evidence.MOVING;
        }
        // A late/incomplete step is contradictory evidence for declaring a stop.
        if (gnssFresh && consecutiveSlowFixes >= 2) {
            if (stepDetectorAvailable && stepDetectorPreviouslyActive
                    && lastDetectorStepAgeMs >= 2_500) {
                return Evidence.STILL_STRONG;
            }
            if (!detectorWalking
                    && (!stepDetectorAvailable || !stepDetectorPreviouslyActive
                        || lastDetectorStepAgeMs >= 2_500)) {
                return Evidence.STILL_MODERATE;
            }
        }
        return Evidence.UNCERTAIN;
    }

    public void reset() {
        lastFixMs = -1;
        geometryFromMs = -1;
        geometricMovingUntil = -1;
        consecutiveSlowFixes = 0;
        consecutiveFastFixes = 0;
        lastGnssStill = false;
        lastGnssMoving = false;
    }
}
