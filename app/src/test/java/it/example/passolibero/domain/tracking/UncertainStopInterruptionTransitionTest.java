package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** P4b/#25: UNKNOWN credits for interrupted STOP match the original branch. */
public final class UncertainStopInterruptionTransitionTest {
    @Test
    public void pendingStopIsSettledToUnknownExactlyOnce() {
        for (long startMs : new long[] {0L, 1L, 2_000L, Long.MAX_VALUE}) {
            for (long pendingMs : new long[] {0L, 1L, 999L, 4_000L, Long.MAX_VALUE}) {
                UncertainStopInterruptionTransition.Effect effect =
                        UncertainStopInterruptionTransition.evaluate(startMs, pendingMs);
                assertTrue(effect.interruptPendingStop());
                assertEquals(pendingMs, effect.unknownCreditMs());
                UncertainStopInterruptionTransition.Effect afterReset =
                        UncertainStopInterruptionTransition.evaluate(-1L, 0L);
                assertFalse(afterReset.interruptPendingStop());
                assertEquals(0L, afterReset.unknownCreditMs());
            }
        }
    }

    @Test
    public void absentStopDoesNotCreditStalePendingTime() {
        for (long startMs : new long[] {Long.MIN_VALUE, -2L, -1L}) {
            for (long pendingMs : new long[] {0L, 1L, 999L, Long.MAX_VALUE}) {
                UncertainStopInterruptionTransition.Effect effect =
                        UncertainStopInterruptionTransition.evaluate(startMs, pendingMs);
                assertFalse(effect.interruptPendingStop());
                assertEquals(0L, effect.unknownCreditMs());
            }
        }
    }

    @Test
    public void effectsPreserveLongInputsAndHaveNoSharedState() {
        UncertainStopInterruptionTransition.Effect first =
                UncertainStopInterruptionTransition.evaluate(100L, Long.MAX_VALUE);
        UncertainStopInterruptionTransition.Effect absent =
                UncertainStopInterruptionTransition.evaluate(-1L, Long.MAX_VALUE);
        UncertainStopInterruptionTransition.Effect next =
                UncertainStopInterruptionTransition.evaluate(200L, Long.MIN_VALUE);
        assertEquals(Long.MAX_VALUE, first.unknownCreditMs());
        assertEquals(0L, absent.unknownCreditMs());
        assertTrue(next.interruptPendingStop());
        assertEquals(Long.MIN_VALUE, next.unknownCreditMs());
    }
}
