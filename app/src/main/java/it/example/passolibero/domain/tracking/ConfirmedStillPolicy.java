package it.example.passolibero.domain.tracking;

/**
 * Pure recognition of an already confirmed STILL state in the legacy motion ledger.
 *
 * <p>Confirmation timing, pending intervals, uncertainty and all time credits remain owned by
 * MotionTimeline. This predicate must not be used to infer stillness from missing evidence.
 */
public final class ConfirmedStillPolicy {
    private ConfirmedStillPolicy() {}

    /** Only an existing STILL state skips fresh STOP confirmation. */
    public static boolean isConfirmed(String committedState) {
        return "STILL".equals(committedState);
    }
}
