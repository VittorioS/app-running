package it.example.passolibero.domain.tracking;

/**
 * Pure decision for whether a fresh activity classification identifies a concrete moving gait.
 *
 * <p>The legacy ledger chooses MOVING_UNKNOWN for every other kind and retains ownership of all
 * time credits, visible state transitions, callback ordering and pending intervals.
 */
public final class ReliableGaitPolicy {
    private ReliableGaitPolicy() {}

    /** Only RUN and WALK identify a reliable gait; missing or other labels do not. */
    public static boolean hasKnownGait(String activityName) {
        return "RUN".equals(activityName) || "WALK".equals(activityName);
    }
}
