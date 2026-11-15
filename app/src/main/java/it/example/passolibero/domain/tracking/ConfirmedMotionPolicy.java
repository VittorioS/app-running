package it.example.passolibero.domain.tracking;

/**
 * Pure classification of a previously confirmed motion state.
 *
 * <p>MOVING_UNKNOWN is confirmed movement even without a reliable RUN/WALK gait. The legacy ledger
 * owns pending intervals, the timing of confirmation and every time-credit side effect.
 */
public final class ConfirmedMotionPolicy {
    private ConfirmedMotionPolicy() {}

    /** True for the three moving states; UNKNOWN and STILL require fresh confirmation. */
    public static boolean isMoving(String committedState) {
        return "RUN".equals(committedState)
                || "WALK".equals(committedState)
                || "MOVING_UNKNOWN".equals(committedState);
    }
}
