package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * P4b/#25: exact parity with the former per-callback selection of STOP confirmation thresholds
 * in MotionTimeline. The G1 ledger tests from PR #106 characterize the effects around this rule.
 */
public final class EffectiveStopThresholdPolicyTest {
    @Test
    public void preservesLegacyChoiceForAllEvidenceAndMinimumBoundaries() {
        String[] evidenceNames = {
            "STILL_STRONG", "STILL_MODERATE", "MOVING", "UNCERTAIN", null, "unexpected"
        };
        long[] minimumStopMsValues = {
            Long.MIN_VALUE, -1L, 0L, 3_999L, 4_000L, 4_001L, 5_000L,
            7_999L, 8_000L, 8_001L, 12_000L, Long.MAX_VALUE
        };
        for (String evidenceName : evidenceNames) {
            for (long minimumStopMs : minimumStopMsValues) {
                long legacyEvidenceThresholdMs =
                        "STILL_STRONG".equals(evidenceName) ? 4_000L : 8_000L;
                long legacyRequiredMs = Math.max(legacyEvidenceThresholdMs, minimumStopMs);
                assertEquals(
                        "evidence=" + evidenceName + " minimumStopMs=" + minimumStopMs,
                        legacyRequiredMs,
                        EffectiveStopThresholdPolicy.requiredStopMs(evidenceName, minimumStopMs));
            }
        }
    }

    @Test
    public void recalculatesOnEachCallbackWithoutRememberingPreviousBudgetOrStrength() {
        assertEquals(12_000L, EffectiveStopThresholdPolicy.requiredStopMs("STILL_STRONG", 12_000L));
        assertEquals(8_000L, EffectiveStopThresholdPolicy.requiredStopMs("STILL_MODERATE", 0L));
        assertEquals(5_000L, EffectiveStopThresholdPolicy.requiredStopMs("STILL_STRONG", 5_000L));
        assertEquals(4_000L, EffectiveStopThresholdPolicy.requiredStopMs("STILL_STRONG", 0L));
    }

    @Test
    public void minimumNeverReducesNominalStrongOrModerateThresholds() {
        assertEquals(4_000L, EffectiveStopThresholdPolicy.requiredStopMs("STILL_STRONG", 3_000L));
        assertEquals(8_000L, EffectiveStopThresholdPolicy.requiredStopMs("STILL_MODERATE", 3_000L));
    }
}
