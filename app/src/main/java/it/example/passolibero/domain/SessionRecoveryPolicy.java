package it.example.passolibero.domain;

import java.util.function.LongSupplier;

/**
 * Pure session-recovery arithmetic. Never manufacture RUN, WALK or STILL
 * intervals from time elapsed while the foreground worker was unavailable.
 *
 * <p>The clocks are supplied by the caller so they are read lazily and in
 * exactly the same order as the legacy TrackingService calculation.
 */
public final class SessionRecoveryPolicy {
    private SessionRecoveryPolicy() {}

    /**
     * Restore a monotonic start timestamp when elapsedRealtime reset at reboot.
     * A valid start timestamp is returned unchanged.
     */
    public static long recoverStartedElapsedMs(
            long savedStartElapsedMs,
            long startedWallMs,
            LongSupplier elapsedRealtimeMs,
            LongSupplier wallClockMs) {
        if (savedStartElapsedMs <= 0
                || savedStartElapsedMs > elapsedRealtimeMs.getAsLong()) {
            long wallDuration = Math.max(0L, wallClockMs.getAsLong() - startedWallMs);
            return Math.max(0L, elapsedRealtimeMs.getAsLong() - wallDuration);
        }
        return savedStartElapsedMs;
    }

    /**
     * Extra elapsed time beyond the last durable snapshot is unknown, not
     * evidence of movement or stillness. Preserves the existing sum/order and
     * clamp rules used by TrackingService.
     */
    public static long unobservedUnknownMs(
            boolean recovering,
            long startedElapsedMs,
            long currentElapsedMs,
            long runMs,
            long walkMs,
            long movingUnknownMs,
            long stillMs,
            long unknownMs) {
        if (!recovering) {
            return 0L;
        }
        long accounted = runMs + walkMs + movingUnknownMs + stillMs + unknownMs;
        long elapsed = Math.max(0L, currentElapsedMs - startedElapsedMs);
        return elapsed > accounted ? elapsed - accounted : 0L;
    }
}
