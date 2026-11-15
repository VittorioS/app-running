package it.example.passolibero.domain.tracking;

/**
 * Pure eligibility decision for revoking an unconfirmed stop after a delayed step report.
 *
 * <p>The ledger owns the stop start timestamp, pending elapsed time, UNKNOWN attribution and
 * reset side effects. A negative stop start means that there is no pending stop.
 */
public final class PendingStopRevocationPolicy {
    private PendingStopRevocationPolicy() {}

    /** A step at the pending-stop boundary counts as overlapping; earlier steps do not. */
    public static boolean shouldRevoke(long stopSinceMs, long stepMs) {
        return stopSinceMs >= 0 && stepMs >= stopSinceMs;
    }
}
