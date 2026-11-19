package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** P4b/#25: match the exact legacy timestamp guard and its clock updates. */
public final class TimelineClockTransitionTest {
    @Test
    public void firstCallbackOnlyAnchorsWithoutCreditingElapsedTime() {
        TimelineClockTransition.Effect effect = TimelineClockTransition.evaluate(-1L, 10_000L);
        assertEquals(TimelineClockTransition.Action.INITIALIZE, effect.action());
        assertEquals(10_000L, effect.lastMsAfter());
        assertEquals(0L, effect.deltaMs());
    }

    @Test
    public void staleAndDuplicateCallbacksPreserveLastAcceptedTimestamp() {
        for (long nowMs : new long[] {Long.MIN_VALUE, -1L, 0L, 999L, 1_000L}) {
            TimelineClockTransition.Effect ignored =
                    TimelineClockTransition.evaluate(1_000L, nowMs);
            assertEquals(TimelineClockTransition.Action.IGNORE, ignored.action());
            assertEquals(1_000L, ignored.lastMsAfter());
            assertEquals(0L, ignored.deltaMs());
        }
    }

    @Test
    public void acceptedIntervalsKeepExactDeltaAndFiveSecondGapBoundary() {
        for (long nowMs : new long[] {1_001L, 5_999L, 6_000L, 6_001L, Long.MAX_VALUE}) {
            TimelineClockTransition.Effect accepted =
                    TimelineClockTransition.evaluate(1_000L, nowMs);
            assertEquals(TimelineClockTransition.Action.ADVANCE, accepted.action());
            assertEquals(nowMs, accepted.lastMsAfter());
            assertEquals(nowMs - 1_000L, accepted.deltaMs());
        }
    }

    @Test
    public void negativeStoredTimestampContinuesToBehaveAsUninitialized() {
        TimelineClockTransition.Effect initial =
                TimelineClockTransition.evaluate(-500L, -1_000L);
        assertEquals(TimelineClockTransition.Action.INITIALIZE, initial.action());
        assertEquals(-1_000L, initial.lastMsAfter());
        assertEquals(0L, initial.deltaMs());

        TimelineClockTransition.Effect next = TimelineClockTransition.evaluate(-1_000L, 0L);
        assertEquals(TimelineClockTransition.Action.INITIALIZE, next.action());
        assertEquals(0L, next.lastMsAfter());
        assertEquals(0L, next.deltaMs());
    }
}
