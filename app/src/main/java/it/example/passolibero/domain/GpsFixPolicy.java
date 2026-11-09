package it.example.passolibero.domain;

/** Pure gates for the existing 1 Hz session GNSS path and conservative distance accounting. */
public final class GpsFixPolicy {
    private GpsFixPolicy() {}

    /** Preserve sprint-only throttling of normal path fixes without altering sprint sampling. */
    public static boolean skipNormalFix(boolean sprintBusy, long previousFixMs, long fixMs) {
        return sprintBusy
                && previousFixMs >= 0
                && fixMs >= previousFixMs
                && fixMs - previousFixMs < 900L;
    }

    /** Uncertain GPS may accrue plausible slow distance, but never confirm RUN or WALK. */
    public static boolean plausibleSlow(
            boolean uncertain,
            double pointSpeedKmh,
            double nativeSpeedMps,
            double speedAccuracyMps) {
        return uncertain
                && Double.isFinite(pointSpeedKmh)
                && pointSpeedKmh >= 1.2
                && pointSpeedKmh < 20
                && Double.isFinite(nativeSpeedMps)
                && nativeSpeedMps >= 0.4
                && Double.isFinite(speedAccuracyMps)
                && speedAccuracyMps <= 0.6;
    }

    /** A confirmed stop blocks tentative distance unless fresh movement evidence exists. */
    public static boolean countDistance(
            boolean moving, boolean plausibleSlow, boolean visibleStill) {
        boolean countDistance = moving || plausibleSlow;
        if (visibleStill && !moving) {
            countDistance = false;
        }
        return countDistance;
    }
}
