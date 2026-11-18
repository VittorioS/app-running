package it.example.passolibero.domain.tracking;

/**
 * Pure effects of a delayed step overlapping an unconfirmed STOP interval.
 *
 * <p>A cumulative step report proves only its last step timestamp, not the preceding batch.
 * The legacy timeline remains responsible for UNKNOWN credits and pending-stop resets.
 */
public final class DelayedStepStopRevocationTransition {
    /** Immutable instructions for the ledger; no time or state is changed in this class. */
    public record Effect(boolean revokePendingStop, long unknownCreditMs) {}

    private DelayedStepStopRevocationTransition() {}

    /** Preserves the original overlap decision and the pending duration verbatim. */
    public static Effect evaluate(long stopSinceMs, long stepMs, long pendingStopMs) {
        boolean revoke = PendingStopRevocationPolicy.shouldRevoke(stopSinceMs, stepMs);
        return new Effect(revoke, revoke ? pendingStopMs : 0L);
    }
}
