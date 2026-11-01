package it.example.passolibero;

/**
 * Optional accelerometer fallback; periodicity is positive movement evidence
 * only. No inertial "still" classification: a motionless telephone need not
 * mean that its owner is standing still.
 */
public final class InertialGaitDetector {
    private double baseline = Double.NaN;
    private boolean above;
    private long lastPeakMs = -1, lastSampleMs = -1;
    private int rhythmicPeaks;

    /** Three-axis raw acceleration in m/s^2, sensor elapsed-realtime timestamp. */
    public void observe(long elapsedMs, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || elapsedMs <= lastSampleMs) return;
        if (lastSampleMs >= 0 && elapsedMs - lastSampleMs > 2_000) reset();
        lastSampleMs = elapsedMs;
        double magnitude = Math.sqrt(x*x + y*y + z*z);
        if (!Double.isFinite(baseline)) baseline = magnitude;
        // High pass with slow baseline drift, independent of phone orientation.
        baseline += 0.035 * (magnitude - baseline);
        double fluctuation = magnitude - baseline;
        if (!above && fluctuation > 1.1) {
            if (lastPeakMs >= 0 && elapsedMs - lastPeakMs >= 260
                    && elapsedMs - lastPeakMs <= 1_200) {
                // A continuing cadence must stay recognized after 2.5 s.
                // The old first-peak window periodically erased the streak.
                rhythmicPeaks = Math.min(8, rhythmicPeaks + 1);
            } else {
                rhythmicPeaks = 1;
            }
            lastPeakMs = elapsedMs;
            above = true;
        } else if (above && fluctuation < 0.2) {
            above = false;
        }
    }

    public boolean gaitAt(long nowMs) {
        return rhythmicPeaks >= 3 && lastPeakMs >= 0
                && nowMs >= lastPeakMs && nowMs - lastPeakMs <= 1_400;
    }

    public void reset() {
        baseline = Double.NaN;
        above = false;
        lastPeakMs = -1;
        lastSampleMs = -1;
        rhythmicPeaks = 0;
    }
}
