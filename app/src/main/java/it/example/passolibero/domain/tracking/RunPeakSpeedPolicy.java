package it.example.passolibero.domain.tracking;

/**
 * Passive run peak-speed aggregation. Does not decide gait or modify accepted GPS distance.
 *
 * <p>Only pre-classified RUN fixes with corroborated movement and fresh, reliable GPS contribute to
 * the peak. Unit: km/h. Double.NaN denotes no accepted sample.
 */
public final class RunPeakSpeedPolicy {
    private RunPeakSpeedPolicy() {}

    /** Preserve the original filter thresholds and floating-point evaluation order. */
    public static double update(
            double currentMax,
            String movement,
            boolean moving,
            boolean fresh,
            double accuracyM,
            double acceptedMeters,
            double speedKmh) {
        if (!"RUN".equals(movement)
                || !moving
                || !fresh
                || !Double.isFinite(accuracyM)
                || accuracyM < 0
                || accuracyM > 15
                || !Double.isFinite(acceptedMeters)
                || acceptedMeters <= 0
                || !Double.isFinite(speedKmh)
                || speedKmh <= 0
                || speedKmh > 40) {
            return currentMax;
        }
        if (!Double.isFinite(currentMax) || currentMax <= 0) {
            return speedKmh;
        }
        return Math.max(currentMax, speedKmh);
    }
}
