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
    private double meanPeriodMs = Double.NaN;

    /** Three-axis raw acceleration in m/s^2, sensor elapsed-realtime timestamp. */
    public void observe(long elapsedMs, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || elapsedMs <= lastSampleMs) return;
        if (lastSampleMs >= 0 && elapsedMs - lastSampleMs > 2_000) reset();
        double dtMs = lastSampleMs < 0 ? 20.0 : elapsedMs - lastSampleMs;
        lastSampleMs = elapsedMs;
        double magnitude = Math.sqrt(x*x + y*y + z*z);
        if (!Double.isFinite(baseline)) baseline = magnitude;
        // High pass with slow baseline drift, independent of phone orientation.
        baseline += (1.0 - Math.exp(-dtMs / 560.0)) * (magnitude - baseline);
        double fluctuation = magnitude - baseline;
        if (!above && fluctuation > 1.1) {
            long period = elapsedMs - lastPeakMs;
            if (lastPeakMs >= 0 && period < 260) return;
            if (lastPeakMs >= 0 && period <= 1_200
                    && (!Double.isFinite(meanPeriodMs)
                        || Math.abs(period - meanPeriodMs) <= meanPeriodMs * 0.30)) {
                rhythmicPeaks = Math.min(8, rhythmicPeaks + 1);
                meanPeriodMs = Double.isFinite(meanPeriodMs)
                        ? meanPeriodMs * 0.75 + period * 0.25 : period;
            } else {
                rhythmicPeaks = 1;
                meanPeriodMs = Double.NaN;
            }
            lastPeakMs = elapsedMs;
            above = true;
        } else if (above && fluctuation < 0.2) {
            above = false;
        }
    }

    public boolean gaitAt(long nowMs) {
        // At least four similarly spaced peaks; three arbitrary shakes are not gait.
        return rhythmicPeaks >= 4 && lastPeakMs >= 0
                && nowMs >= lastPeakMs && nowMs - lastPeakMs <= 1_400;
    }

    public void reset() {
        baseline = Double.NaN;
        above = false;
        lastPeakMs = -1;
        lastSampleMs = -1;
        rhythmicPeaks = 0;
        meanPeriodMs = Double.NaN;
    }
}
