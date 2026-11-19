package it.example.passolibero.domain.tracking;

/**
 * Pure normalization of five persisted counters when a tracking snapshot is restored.
 *
 * <p>Negative values become zero. The legacy ledger owns field assignments, clock reset,
 * committed/displayed UNKNOWN state, and pending-buffer resets.
 */
public final class TrackingRestoreCountersTransition {
    /** Normalized counters, with no hidden state or Android dependencies. */
    public record Effect(
            long runMs, long walkMs, long stillMs, long unknownMs, long movingUnknownMs) {}

    private TrackingRestoreCountersTransition() {}

    /** Preserve the original Math.max(0, value) for each counter independently. */
    public static Effect evaluate(
            long runMs, long walkMs, long stillMs, long unknownMs, long movingUnknownMs) {
        return new Effect(
                Math.max(0, runMs),
                Math.max(0, walkMs),
                Math.max(0, stillMs),
                Math.max(0, unknownMs),
                Math.max(0, movingUnknownMs));
    }
}
