package it.example.passolibero.domain.tracking;

/**
 * Pure transition effect for fresh MOVING evidence without confirmed movement.
 *
 * <p>The legacy ledger owns timestamps, pending time, committed/visible state and all credits. This
 * transition only computes the existing candidate interval and confirmation decision.
 */
public final class MovingStartTransition {
    /** Immutable intermediate values; no state or counters are modified by this class. */
    public record Effect(
            long movingSinceMs, long pendingMs, boolean confirmed, boolean useCurrentGait) {}

    private MovingStartTransition() {}

    /**
     * Preserves the legacy two-second confirmation and chooses gait from the current callback,
     * never from a prior pending candidate.
     */
    public static Effect evaluate(
            long nowMs,
            long deltaMs,
            long previousMovingSinceMs,
            long unresolvedMs,
            String currentGaitName) {
        long movingSinceMs = previousMovingSinceMs < 0 ? nowMs - deltaMs : previousMovingSinceMs;
        long pendingMs = unresolvedMs + deltaMs;
        boolean confirmed = MovementStartConfirmationPolicy.isConfirmed(nowMs - movingSinceMs);
        boolean useCurrentGait = confirmed && ReliableGaitPolicy.hasKnownGait(currentGaitName);
        return new Effect(movingSinceMs, pendingMs, confirmed, useCurrentGait);
    }
}
