package it.example.passolibero.domain.tracking;

/**
 * Pure effect of a MOVING callback after movement has already been confirmed.
 *
 * <p>Pending time belongs to the previous confirmed movement. Only the new callback interval may
 * use the current reliable RUN/WALK gait. The legacy ledger owns all counter and state changes.
 */
public final class ConfirmedMovingIntervalTransition {
    /** Immutable instructions for the previous and current intervals; no time is credited here. */
    public record Effect(long previousCreditMs, long currentCreditMs, boolean useCurrentGait) {}

    private ConfirmedMovingIntervalTransition() {}

    /**
     * Keeps the previous pending interval separate from the fresh interval and never invents gait.
     */
    public static Effect evaluate(
            long unresolvedMs, long currentDeltaMs, String currentActivityName) {
        return new Effect(
                unresolvedMs, currentDeltaMs, ReliableGaitPolicy.hasKnownGait(currentActivityName));
    }
}
