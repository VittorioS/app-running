package it.example.passolibero.domain.tracking;

/**
 * Pure credits for a STILL callback after stillness was already confirmed.
 *
 * <p>Previously unresolved time and the current callback interval both belong to confirmed STILL,
 * but are credited separately in the original ledger order. This class has no mutable state.
 */
public final class ConfirmedStillIntervalTransition {
    /** Proposed ledger credits; the caller owns all time counters and resets. */
    public record Effect(long previousCreditMs, long currentCreditMs) {}

    private ConfirmedStillIntervalTransition() {}

    /** Returns the original durations verbatim, including Java long arithmetic semantics. */
    public static Effect evaluate(long unresolvedMs, long currentDeltaMs) {
        return new Effect(unresolvedMs, currentDeltaMs);
    }
}
