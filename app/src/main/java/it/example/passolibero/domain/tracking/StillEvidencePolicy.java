package it.example.passolibero.domain.tracking;

/**
 * Pure classification of current evidence that may enter the legacy stillness branch.
 *
 * <p>This policy does not confirm a stop, choose a threshold, track time, or mutate the ledger.
 * Missing, moving, and uncertain evidence cannot start or confirm a pending stop.
 */
public final class StillEvidencePolicy {
    private StillEvidencePolicy() {}

    /** True only for the two existing stillness evidence strengths. */
    public static boolean isStill(String evidenceName) {
        return "STILL_STRONG".equals(evidenceName) || "STILL_MODERATE".equals(evidenceName);
    }
}
