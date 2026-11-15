package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** P4b/#25: exact parity with the original stop-confirmation condition in MotionTimeline. */
public final class StopConfirmationPolicyTest {
    @Test
    public void confirmsAtExactThresholdForStrongModerateAndDelayedStepBudgets() {
        for (long requiredMs : new long[] {4_000L, 8_000L, 12_000L}) {
            assertFalse(StopConfirmationPolicy.isConfirmed(requiredMs - 1L, requiredMs));
            assertTrue(StopConfirmationPolicy.isConfirmed(requiredMs, requiredMs));
            assertTrue(StopConfirmationPolicy.isConfirmed(requiredMs + 1L, requiredMs));
        }
    }

    @Test
    public void preservesLegacyComparisonForAllExtremeAndZeroInputs() {
        long[] elapsedMs = {Long.MIN_VALUE, -1L, 0L, 1L, 3_999L, 4_000L, Long.MAX_VALUE};
        long[] requiredMs = {Long.MIN_VALUE, -1L, 0L, 4_000L, 8_000L, 12_000L, Long.MAX_VALUE};
        for (long elapsed : elapsedMs) {
            for (long required : requiredMs) {
                assertEquals(
                        "elapsed " + elapsed + " required " + required,
                        elapsed >= required,
                        StopConfirmationPolicy.isConfirmed(elapsed, required));
            }
        }
    }
}
