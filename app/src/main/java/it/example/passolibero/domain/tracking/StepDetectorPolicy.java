package it.example.passolibero.domain.tracking;

/** Admission rules for individual Android step detector events. */
public final class StepDetectorPolicy {
    // A detector callback older than the five-second movement evidence window is stale.
    private static final long MAX_AGE_NANOS = 5_000_000_000L;

    private StepDetectorPolicy() {}

    public static boolean accepts(
            float value,
            long eventNanos,
            long lastAcceptedNanos,
            long receivedNanos,
            long sessionStartedMs) {
        return value == 1.0f
                && eventNanos >= 0
                && eventNanos > lastAcceptedNanos
                && eventNanos <= receivedNanos
                && receivedNanos - eventNanos <= MAX_AGE_NANOS
                && eventNanos / 1_000_000L >= sessionStartedMs;
    }
}
