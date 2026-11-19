package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** P4b/#25: restored counters match the legacy five independent clamps. */
public final class TrackingRestoreCountersTransitionTest {
    @Test
    public void individuallyClampsNegativeCounterValues() {
        TrackingRestoreCountersTransition.Effect effect =
                TrackingRestoreCountersTransition.evaluate(-1L, 2L, -3L, 4L, -5L);
        assertEquals(0L, effect.runMs());
        assertEquals(2L, effect.walkMs());
        assertEquals(0L, effect.stillMs());
        assertEquals(4L, effect.unknownMs());
        assertEquals(0L, effect.movingUnknownMs());
    }

    @Test
    public void preservesZeroAndExtremeLongValuesWithoutSaturation() {
        TrackingRestoreCountersTransition.Effect values =
                TrackingRestoreCountersTransition.evaluate(
                        Long.MAX_VALUE, 0L, Long.MIN_VALUE, Long.MAX_VALUE, -1L);
        assertEquals(Long.MAX_VALUE, values.runMs());
        assertEquals(0L, values.walkMs());
        assertEquals(0L, values.stillMs());
        assertEquals(Long.MAX_VALUE, values.unknownMs());
        assertEquals(0L, values.movingUnknownMs());
    }

    @Test
    public void independentRestoresDoNotAccumulatePreviousCounters() {
        TrackingRestoreCountersTransition.Effect first =
                TrackingRestoreCountersTransition.evaluate(1L, 2L, 3L, 4L, 5L);
        TrackingRestoreCountersTransition.Effect second =
                TrackingRestoreCountersTransition.evaluate(10L, -2L, 0L, 30L, -4L);
        assertEquals(1L, first.runMs());
        assertEquals(5L, first.movingUnknownMs());
        assertEquals(10L, second.runMs());
        assertEquals(0L, second.walkMs());
        assertEquals(0L, second.stillMs());
        assertEquals(30L, second.unknownMs());
        assertEquals(0L, second.movingUnknownMs());
    }
}
