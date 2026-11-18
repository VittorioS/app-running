package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** P4b/#25: moving recovery preserves the original pending-stop effect exactly. */
public final class MovingPendingStopRecoveryTransitionTest {
    @Test
    public void everyActiveCandidateCreditsItsOriginalPendingDuration() {
        long[] starts = {0L, 1L, 1_999L, 2_000L, Long.MAX_VALUE};
        long[] pending = {0L, 1L, 999L, 3_999L, 8_000L, Long.MAX_VALUE, Long.MIN_VALUE};

        for (long stopSinceMs : starts) {
            for (long pendingMs : pending) {
                MovingPendingStopRecoveryTransition.Effect effect =
                        MovingPendingStopRecoveryTransition.evaluate(stopSinceMs, pendingMs);
                assertTrue(effect.recoverPendingStop());
                assertEquals(pendingMs, effect.previousCreditMs());
            }
        }
    }

    @Test
    public void absentPendingStopCannotCreditAnyPreviousActivity() {
        for (long stopSinceMs : new long[] {Long.MIN_VALUE, -2L, -1L}) {
            for (long pendingMs : new long[] {0L, 1L, 999L, Long.MAX_VALUE}) {
                MovingPendingStopRecoveryTransition.Effect effect =
                        MovingPendingStopRecoveryTransition.evaluate(stopSinceMs, pendingMs);
                assertFalse(effect.recoverPendingStop());
                assertEquals(0L, effect.previousCreditMs());
            }
        }
    }

    @Test
    public void independentCallsNeverRememberPreviousPendingStop() {
        MovingPendingStopRecoveryTransition.Effect first =
                MovingPendingStopRecoveryTransition.evaluate(2_000L, 1_000L);
        MovingPendingStopRecoveryTransition.Effect cleared =
                MovingPendingStopRecoveryTransition.evaluate(-1L, 0L);
        MovingPendingStopRecoveryTransition.Effect later =
                MovingPendingStopRecoveryTransition.evaluate(4_000L, 250L);

        assertEquals(1_000L, first.previousCreditMs());
        assertFalse(cleared.recoverPendingStop());
        assertEquals(0L, cleared.previousCreditMs());
        assertTrue(later.recoverPendingStop());
        assertEquals(250L, later.previousCreditMs());
    }
}
