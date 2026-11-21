package it.example.passolibero.domain.tracking;

/**
 * Pure destination decision for unresolved time when moving evidence returns.
 *
 * <p>Only a previously confirmed moving state may receive the old uncertain interval. Missing,
 * STILL or UNKNOWN state leaves it UNKNOWN. The legacy ledger owns all credits and resets.
 */
public final class MovingUncertaintyRecoveryTransition {
    public enum Destination {
        PREVIOUS_CONFIRMED_MOVEMENT,
        UNKNOWN
    }

    /** Immutable instruction; the duration is not modified or credited by this class. */
    public record Effect(Destination destination, long creditMs) {}

    /** Whether the old UNCERTAIN window needs settlement before fresh MOVING evidence. */
    public record WindowEffect(
            boolean recoverPendingWindow, Destination destination, long creditMs) {}

    private MovingUncertaintyRecoveryTransition() {}

    /**
     * Preserve the original active-window guard: only a nonnegative uncertainty anchor may
     * settle a pending interval. An absent window must not consume any stale unresolved time.
     */
    public static WindowEffect resolveWindow(
            long uncertainSinceMs, String previousCommittedState, long unresolvedMs) {
        if (uncertainSinceMs < 0) {
            return new WindowEffect(false, Destination.UNKNOWN, 0L);
        }
        Effect oldRecovery = settle(previousCommittedState, unresolvedMs);
        return new WindowEffect(true, oldRecovery.destination(), oldRecovery.creditMs());
    }

    /** Decide the destination using the state confirmed before the new MOVING callback. */
    public static Effect settle(String previousCommittedState, long unresolvedMs) {
        Destination destination =
                ConfirmedMotionPolicy.isMoving(previousCommittedState)
                        ? Destination.PREVIOUS_CONFIRMED_MOVEMENT
                        : Destination.UNKNOWN;
        return new Effect(destination, unresolvedMs);
    }
}
