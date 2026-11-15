package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** P4b/#25: exact equivalence with the legacy MotionTimeline worker-gap predicate. */
public final class UnobservedIntervalPolicyTest {
    @Test
    public void exactFiveSecondThresholdIsInclusiveForObservedIntervals() {
        assertEquals(5_000L, UnobservedIntervalPolicy.UNKNOWN_GRACE_MS);
        assertFalse(UnobservedIntervalPolicy.isUnobservedGap(4_999L));
        assertFalse(UnobservedIntervalPolicy.isUnobservedGap(5_000L));
        assertTrue(UnobservedIntervalPolicy.isUnobservedGap(5_001L));
    }

    @Test
    public void negativeZeroAndExtremeDurationsPreserveLegacyComparison() {
        for (long durationMs :
                new long[] {Long.MIN_VALUE, -1L, 0L, 1L, 5_000L, 5_001L, Long.MAX_VALUE}) {
            assertEquals(
                    "interval " + durationMs,
                    durationMs > 5_000L,
                    UnobservedIntervalPolicy.isUnobservedGap(durationMs));
        }
    }
}
