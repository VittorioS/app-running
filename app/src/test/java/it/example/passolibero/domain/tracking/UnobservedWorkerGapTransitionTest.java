package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** P4b/#25: unobserved gap effects match the strict legacy policy at its boundaries. */
public final class UnobservedWorkerGapTransitionTest {
    @Test
    public void exactlyFiveSecondsAndShorterIntervalsDoNotSettleAsUnknown() {
        for (long intervalMs :
                new long[] {Long.MIN_VALUE, -1L, 0L, 1L, 4_999L, 5_000L}) {
            UnobservedWorkerGapTransition.Effect effect =
                    UnobservedWorkerGapTransition.evaluate(intervalMs);
            assertFalse(effect.unobserved());
            assertEquals(0L, effect.unknownCreditMs());
        }
    }

    @Test
    public void longerGapsProposeExactlyTheMissingIntervalAsUnknown() {
        for (long intervalMs : new long[] {5_001L, 6_000L, Long.MAX_VALUE}) {
            UnobservedWorkerGapTransition.Effect effect =
                    UnobservedWorkerGapTransition.evaluate(intervalMs);
            assertTrue(effect.unobserved());
            assertEquals(intervalMs, effect.unknownCreditMs());
        }
    }

    @Test
    public void independentCallbacksDoNotRetainAPreviousGap() {
        UnobservedWorkerGapTransition.Effect gap =
                UnobservedWorkerGapTransition.evaluate(5_001L);
        UnobservedWorkerGapTransition.Effect fresh =
                UnobservedWorkerGapTransition.evaluate(1_000L);
        UnobservedWorkerGapTransition.Effect another =
                UnobservedWorkerGapTransition.evaluate(8_000L);

        assertEquals(5_001L, gap.unknownCreditMs());
        assertFalse(fresh.unobserved());
        assertEquals(0L, fresh.unknownCreditMs());
        assertEquals(8_000L, another.unknownCreditMs());
    }
}
