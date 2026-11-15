package it.example.passolibero.domain.tracking;

/**
 * Pure timing rule for confirming the start of movement.
 *
 * <p>The legacy ledger owns accumulation, visible states and gait attribution. This policy only
 * answers whether fresh moving evidence has reached its pre-existing confirmation interval.
 */
public final class MovementStartConfirmationPolicy {
    public static final long START_CONFIRM_MS = 2_000L;

    private MovementStartConfirmationPolicy() {}

    /** At exactly two seconds, the pending movement start becomes confirmed. */
    public static boolean isConfirmed(long elapsedMovingEvidenceMs) {
        return elapsedMovingEvidenceMs >= START_CONFIRM_MS;
    }
}
