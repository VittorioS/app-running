package it.example.passolibero.domain.tracking;

/**
 * Pure selection of the effective confirmation threshold for pending stillness.
 *
 * <p>The legacy ledger supplies the evidence from the current callback and the current minimum
 * imposed by delayed step delivery. This policy does not cache the initial evidence, observe clocks,
 * confirm a stop, or credit any time.
 */
public final class EffectiveStopThresholdPolicy {
    private static final long STRONG_STOP_MS = 4_000L;
    private static final long MODERATE_STOP_MS = 8_000L;

    private EffectiveStopThresholdPolicy() {}

    /** The legacy fallback for any evidence other than STILL_STRONG is the moderate threshold. */
    public static long requiredStopMs(String evidenceName, long minimumStopMs) {
        long evidenceStopMs =
                "STILL_STRONG".equals(evidenceName) ? STRONG_STOP_MS : MODERATE_STOP_MS;
        return Math.max(evidenceStopMs, minimumStopMs);
    }
}
