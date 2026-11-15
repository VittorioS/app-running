package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** P4b/#25: bit-for-bit predicate parity before and after the cumulative UNCERTAIN seam. */
public final class UncertaintyGracePolicyTest {
    @Test
    public void stillPendingJustBelowThresholdButNotAtOrAbove() {
        assertEquals(5_000L, UnobservedIntervalPolicy.UNKNOWN_GRACE_MS);
        assertTrue(UncertaintyGracePolicy.shouldKeepPending(0L));
        assertTrue(UncertaintyGracePolicy.shouldKeepPending(4_999L));
        assertFalse(UncertaintyGracePolicy.shouldKeepPending(5_000L));
        assertFalse(UncertaintyGracePolicy.shouldKeepPending(5_001L));
    }

    @Test
    public void retainsExactComparisonForAllBoundaryAndExtremeLongInputs() {
        for (long elapsedMs :
                new long[] {
                    Long.MIN_VALUE, -5_000L, -1L, 0L, 1L, 4_999L, 5_000L, 5_001L, Long.MAX_VALUE
                }) {
            assertEquals(
                    "uncertainty elapsed " + elapsedMs,
                    elapsedMs < 5_000L,
                    UncertaintyGracePolicy.shouldKeepPending(elapsedMs));
        }
    }
}
