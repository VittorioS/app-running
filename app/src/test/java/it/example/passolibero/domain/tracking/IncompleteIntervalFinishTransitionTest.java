package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** P4b/#25: final UNKNOWN credits must match the original finish() branch. */
public final class IncompleteIntervalFinishTransitionTest {
    @Test
    public void keepsPendingStopAndUnresolvedEvidenceSeparate() {
        long[] stops = {0L, 1L, 999L, 4_000L, Long.MAX_VALUE};
        long[] uncertain = {0L, 1L, 999L, 4_999L, Long.MAX_VALUE};
        for (long stopMs : stops) {
            for (long uncertainMs : uncertain) {
                IncompleteIntervalFinishTransition.Effect effect =
                        IncompleteIntervalFinishTransition.evaluate(stopMs, uncertainMs);
                assertEquals(stopMs, effect.pendingStopUnknownMs());
                assertEquals(uncertainMs, effect.unresolvedUnknownMs());
            }
        }
    }

    @Test
    public void doesNotPreSumClampOrNormalizeLongValues() {
        IncompleteIntervalFinishTransition.Effect large =
                IncompleteIntervalFinishTransition.evaluate(Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, large.pendingStopUnknownMs());
        assertEquals(Long.MAX_VALUE, large.unresolvedUnknownMs());

        IncompleteIntervalFinishTransition.Effect negative =
                IncompleteIntervalFinishTransition.evaluate(Long.MIN_VALUE, -1L);
        assertEquals(Long.MIN_VALUE, negative.pendingStopUnknownMs());
        assertEquals(-1L, negative.unresolvedUnknownMs());
    }

    @Test
    public void repeatedFinishesCannotRetainPreviousPendingDurations() {
        IncompleteIntervalFinishTransition.Effect before =
                IncompleteIntervalFinishTransition.evaluate(1_000L, 500L);
        IncompleteIntervalFinishTransition.Effect afterReset =
                IncompleteIntervalFinishTransition.evaluate(0L, 0L);
        IncompleteIntervalFinishTransition.Effect next =
                IncompleteIntervalFinishTransition.evaluate(0L, 250L);
        assertEquals(1_000L, before.pendingStopUnknownMs());
        assertEquals(500L, before.unresolvedUnknownMs());
        assertEquals(0L, afterReset.pendingStopUnknownMs());
        assertEquals(0L, afterReset.unresolvedUnknownMs());
        assertEquals(0L, next.pendingStopUnknownMs());
        assertEquals(250L, next.unresolvedUnknownMs());
    }
}
