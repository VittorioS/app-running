package it.example.passolibero.domain.tracking;

/**
 * Pure predicate for confirming a pending stop after sufficient continuous stillness evidence.
 *
 * <p>The effective threshold policy determines the required interval from the current evidence
 * strength and delayed-step delivery budget. This policy only compares that interval with elapsed
 * stillness. Crediting time and changing visible state remain responsibilities of the ledger.
 */
public final class StopConfirmationPolicy {
    private StopConfirmationPolicy() {}

    /** Confirms at the exact required boundary, not one millisecond earlier. */
    public static boolean isConfirmed(long elapsedStillEvidenceMs, long requiredStopMs) {
        return elapsedStillEvidenceMs >= requiredStopMs;
    }
}
