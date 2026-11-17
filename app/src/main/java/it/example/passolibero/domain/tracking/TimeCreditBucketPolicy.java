package it.example.passolibero.domain.tracking;

/**
 * Pure selection of the legacy ledger bucket for a credited time interval.
 *
 * <p>The caller remains responsible for rejecting nonpositive durations and for updating counters.
 * A missing or unrecognized state is UNKNOWN, never an inferred movement or stop.
 */
public final class TimeCreditBucketPolicy {
    public enum Bucket {
        RUN,
        WALK,
        STILL,
        MOVING_UNKNOWN,
        UNKNOWN
    }

    private TimeCreditBucketPolicy() {}

    /** Classifies the destination only; never credits any time. */
    public static Bucket bucketFor(String stateName) {
        if ("RUN".equals(stateName)) return Bucket.RUN;
        if ("WALK".equals(stateName)) return Bucket.WALK;
        if ("STILL".equals(stateName)) return Bucket.STILL;
        if ("MOVING_UNKNOWN".equals(stateName)) return Bucket.MOVING_UNKNOWN;
        return Bucket.UNKNOWN;
    }
}
