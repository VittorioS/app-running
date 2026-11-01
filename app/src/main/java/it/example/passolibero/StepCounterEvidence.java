package it.example.passolibero;

/** Timestamped cumulative steps; delayed reports never become fresh steps. */
public final class StepCounterEvidence {
    public static final long MAX_DELIVERY_DELAY_MS = 10_000;
    // Allow the documented delivery latency plus scheduling margin before a stop.
    public static final long STOP_CONFIRM_MS = MAX_DELIVERY_DELAY_MS + 2_000;
    private double lastCount = Double.NaN;
    private long lastReportMs = -1, lastProgressMs = -1, intervalStartMs = -1;

    public int observe(long stampMs, double count, long receivedMs) {
        if (stampMs < 0 || stampMs > receivedMs || stampMs <= lastReportMs
                || !Double.isFinite(count) || count < 0) return 0;
        intervalStartMs = lastReportMs;
        double delta = Double.isFinite(lastCount) ? count - lastCount : 0;
        lastCount = count;
        lastReportMs = stampMs;
        if (delta < 0) lastProgressMs = -1;
        if (delta < 1) return 0; // First report or counter reset is a new baseline.
        lastProgressMs = stampMs;
        return (int) Math.min(Integer.MAX_VALUE, delta);
    }

    public boolean progressRecent(long nowMs) {
        return lastProgressMs >= 0 && nowMs >= lastProgressMs && nowMs - lastProgressMs <= 3_000;
    }

    public boolean canAmendPendingStop(long nowMs) {
        return intervalStartMs >= 0 && lastProgressMs >= 0 && nowMs >= lastProgressMs
                && nowMs - lastProgressMs <= MAX_DELIVERY_DELAY_MS;
    }

    public long lastStepMs() { return lastProgressMs; }

    public void reset() {
        lastCount = Double.NaN;
        lastReportMs = -1;
        lastProgressMs = -1;
        intervalStartMs = -1;
    }
}
