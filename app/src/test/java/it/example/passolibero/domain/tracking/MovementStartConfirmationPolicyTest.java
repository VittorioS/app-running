package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** P4b/#25: decision parity with the legacy MotionTimeline start confirmation comparison. */
public final class MovementStartConfirmationPolicyTest {
    @Test
    public void movingRemainsPendingBeforeTwoSecondsAndConfirmsAtTheBoundary() {
        assertEquals(2_000L, MovementStartConfirmationPolicy.START_CONFIRM_MS);
        assertFalse(MovementStartConfirmationPolicy.isConfirmed(0L));
        assertFalse(MovementStartConfirmationPolicy.isConfirmed(1_999L));
        assertTrue(MovementStartConfirmationPolicy.isConfirmed(2_000L));
        assertTrue(MovementStartConfirmationPolicy.isConfirmed(2_001L));
    }

    @Test
    public void keepsExactLegacyComparisonForExtremeInputs() {
        assertEquals(
                Long.MIN_VALUE >= 2_000L,
                MovementStartConfirmationPolicy.isConfirmed(Long.MIN_VALUE));
        assertEquals(-1L >= 2_000L, MovementStartConfirmationPolicy.isConfirmed(-1L));
        assertEquals(1L >= 2_000L, MovementStartConfirmationPolicy.isConfirmed(1L));
        assertEquals(
                Long.MAX_VALUE >= 2_000L,
                MovementStartConfirmationPolicy.isConfirmed(Long.MAX_VALUE));
    }
}
