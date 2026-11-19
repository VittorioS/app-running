package it.example.passolibero.domain.tracking;

/**
 * Pure decision and UNKNOWN credit for an unobserved worker callback interval.
 *
 * <p>It reuses the existing strict five-second policy. The legacy timeline still settles
 * pending intervals, applies credits, resets confirmed activity, and owns callback timing.
 */
public final class UnobservedWorkerGapTransition {
    /** Proposed effect, without any ledger mutation or retained state. */
    public record Effect(boolean unobserved, long unknownCreditMs) {}

    private UnobservedWorkerGapTransition() {}

    /** Preserve the previous gap threshold and pass the full interval through unchanged. */
    public static Effect evaluate(long callbackIntervalMs) {
        boolean unobserved = UnobservedIntervalPolicy.isUnobservedGap(callbackIntervalMs);
        return new Effect(unobserved, unobserved ? callbackIntervalMs : 0L);
    }
}
