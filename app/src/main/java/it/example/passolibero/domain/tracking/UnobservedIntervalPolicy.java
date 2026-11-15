package it.example.passolibero.domain.tracking;

/**
 * The existing monotonic ledger treats a callback gap longer than the grace period as unobserved.
 *
 * <p>This pure decision does not settle pending intervals, classify activity, or mutate session
 * state. The legacy MotionTimeline retains those effects and their original ordering.
 */
public final class UnobservedIntervalPolicy {
    public static final long UNKNOWN_GRACE_MS = 5_000L;

    private UnobservedIntervalPolicy() {}

    /** Exactly the grace interval is still observed; only a strictly longer gap is unknown. */
    public static boolean isUnobservedGap(long elapsedSincePreviousCallbackMs) {
        return elapsedSincePreviousCallbackMs > UNKNOWN_GRACE_MS;
    }
}
