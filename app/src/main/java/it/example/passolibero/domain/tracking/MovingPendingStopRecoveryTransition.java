package it.example.passolibero.domain.tracking;

/**
 * Pure effects when fresh MOVING evidence interrupts an unconfirmed STOP.
 *
 * <p>Unconfirmed microstops are credited to the previous committed activity, never to the
 * current callback's gait. The legacy timeline owns the time ledger and resets.
 */
public final class MovingPendingStopRecoveryTransition {
    /** Immutable instructions; no counters or timestamps are mutated here. */
    public record Effect(boolean recoverPendingStop, long previousCreditMs) {}

    private MovingPendingStopRecoveryTransition() {}

    /** Keep the original pending-stop guard and duration, without clamping or backfill. */
    public static Effect evaluate(long stopSinceMs, long stopPendingMs) {
        boolean recover = stopSinceMs >= 0;
        return new Effect(recover, recover ? stopPendingMs : 0L);
    }
}
