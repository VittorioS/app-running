package it.example.passolibero.domain.tracking;

/**
 * Pure decision for the pending interval of continuously uncertain motion evidence.
 *
 * <p>Unlike a gap between callbacks, each incoming callback can be timely while evidence stays
 * uncertain. Only the caller decides how to credit elapsed time or update visible movement.
 */
public final class UncertaintyGracePolicy {
    private UncertaintyGracePolicy() {}

    /** Pending intervals persist strictly below the existing 5-second grace threshold. */
    public static boolean shouldKeepPending(long uncertainElapsedMs) {
        return uncertainElapsedMs < UnobservedIntervalPolicy.UNKNOWN_GRACE_MS;
    }
}
