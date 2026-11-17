package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** P4b/#25: confirmed STILL credits match the original legacy ledger branch exactly. */
public final class ConfirmedStillIntervalTransitionTest {
    @Test
    public void keepsPreviousUncertaintySeparateFromCurrentStillInterval() {
        long[] pendingValues = {0L, 1L, 999L, 4_999L, 5_000L, Long.MAX_VALUE};
        long[] deltaValues = {0L, 1L, 1_000L, 5_001L, Long.MAX_VALUE};
        for (long pendingMs : pendingValues) {
            for (long deltaMs : deltaValues) {
                ConfirmedStillIntervalTransition.Effect effect =
                        ConfirmedStillIntervalTransition.evaluate(pendingMs, deltaMs);
                assertEquals(pendingMs, effect.previousCreditMs());
                assertEquals(deltaMs, effect.currentCreditMs());
            }
        }
    }

    @Test
    public void doesNotClampOrCombineDurationsAtLongBoundaries() {
        ConfirmedStillIntervalTransition.Effect max =
                ConfirmedStillIntervalTransition.evaluate(Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, max.previousCreditMs());
        assertEquals(Long.MAX_VALUE, max.currentCreditMs());

        ConfirmedStillIntervalTransition.Effect min =
                ConfirmedStillIntervalTransition.evaluate(Long.MIN_VALUE, -1L);
        assertEquals(Long.MIN_VALUE, min.previousCreditMs());
        assertEquals(-1L, min.currentCreditMs());
    }

    @Test
    public void independentCallbacksDoNotRetainPreviousPendingTime() {
        ConfirmedStillIntervalTransition.Effect first =
                ConfirmedStillIntervalTransition.evaluate(2_000L, 1_000L);
        ConfirmedStillIntervalTransition.Effect second =
                ConfirmedStillIntervalTransition.evaluate(0L, 250L);
        assertEquals(2_000L, first.previousCreditMs());
        assertEquals(1_000L, first.currentCreditMs());
        assertEquals(0L, second.previousCreditMs());
        assertEquals(250L, second.currentCreditMs());
    }
}
