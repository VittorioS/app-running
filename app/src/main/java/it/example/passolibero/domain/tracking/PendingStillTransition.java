package it.example.passolibero.domain.tracking;

/**
 * Pure effects of new STILL evidence while STOP confirmation is still pending.
 *
 * <p>The ledger owns all timestamps, pending buffers, state transitions, and time credits. This
 * transition only evaluates the candidate using the current callback's evidence and stop budget.
 */
public final class PendingStillTransition {
    /** Instructions for the ledger; no counter or state is mutated here. */
    public record Effect(
            long stopSinceMs,
            long pendingStopMs,
            boolean confirmed,
            long stillCreditMs,
            long unknownCreditMs) {}

    private PendingStillTransition() {}

    /**
     * Preserves the original pending-stop start, current callback's confirmation threshold and
     * credits: confirmed STOP backdates stillness but never retroactively classifies unresolved
     * uncertainty as STILL.
     */
    public static Effect evaluate(
            long nowMs,
            long deltaMs,
            long previousStopSinceMs,
            long previousStopPendingMs,
            long unresolvedMs,
            String currentEvidenceName,
            long minimumStopMs) {
        long stopSinceMs = previousStopSinceMs < 0 ? nowMs - deltaMs : previousStopSinceMs;
        long pendingStopMs = previousStopPendingMs + deltaMs;
        long requiredStopMs =
                EffectiveStopThresholdPolicy.requiredStopMs(currentEvidenceName, minimumStopMs);
        boolean confirmed = StopConfirmationPolicy.isConfirmed(nowMs - stopSinceMs, requiredStopMs);
        return new Effect(
                stopSinceMs,
                pendingStopMs,
                confirmed,
                confirmed ? pendingStopMs : 0L,
                confirmed ? unresolvedMs : 0L);
    }
}
