package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * ADR-002/#25 P4b G1: characterization of the existing transition sequence in
 * TrackingService.advanceTimeline(), before moving any production code.
 *
 * These deterministic JVM tests exercise the existing MotionTimeline and
 * ActivityEvidence. They do not exercise Android callbacks, SQLite, the
 * Service worker, speech, or a physical Android 11 device.
 */
public final class TrackingStateTransitionCharacterizationTest {
    private static void assertConserved(MotionTimeline timeline, long elapsedMs) {
        timeline.finish();
        assertEquals(
                timeline.runMs() + timeline.walkMs() + timeline.movingUnknownMs(),
                timeline.movingMs());
        assertEquals(
                elapsedMs,
                timeline.movingMs() + timeline.stillMs() + timeline.unknownMs());
    }

    @Test
    public void confirmedStopInvalidatesPreviousGaitWithoutErasingCreditedWalk() {
        MotionTimeline timeline = new MotionTimeline();
        ActivityEvidence activity = new ActivityEvidence();

        timeline.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        // Simulate fresh, independently confirmed WALK evidence from accepted fixes.
        for (long nowMs = 1000; nowMs <= 3000; nowMs += 1000) {
            activity.observe(nowMs, nowMs, 4, 2, 6);
            timeline.advance(
                    nowMs, MotionEvidencePolicy.Evidence.MOVING, activity.current(nowMs));
        }
        assertEquals(MovementClassifier.Kind.WALK, timeline.visible());
        assertEquals(1000, timeline.walkMs());
        assertEquals(2000, timeline.movingUnknownMs());

        for (long nowMs = 4000; nowMs <= 7000; nowMs += 1000) {
            MovementClassifier.Kind previous = timeline.visible();
            timeline.advance(
                    nowMs,
                    MotionEvidencePolicy.Evidence.STILL_STRONG,
                    activity.current(nowMs));
            MovementClassifier.Kind current = timeline.visible();
            // Exactly the reset trigger currently present in TrackingService.
            if (previous != current
                    && (current == MovementClassifier.Kind.STILL
                            || current == MovementClassifier.Kind.UNKNOWN)) {
                activity.onMotionState(current);
            }
        }
        assertEquals(MovementClassifier.Kind.STILL, timeline.visible());
        assertEquals(MovementClassifier.Kind.UNKNOWN, activity.current(7000));
        assertEquals(1000, timeline.walkMs());
        assertEquals(2000, timeline.movingUnknownMs());
        assertEquals(4000, timeline.stillMs());
        assertConserved(timeline, 7000);
    }

    @Test
    public void uncertaintyExpiresToUnknownWithoutBackfillingGaitOrStop() {
        MotionTimeline timeline = new MotionTimeline();
        timeline.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        for (long nowMs = 1000; nowMs <= 3000; nowMs += 1000) {
            timeline.advance(
                    nowMs, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        }
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, timeline.visible());
        assertEquals(3000, timeline.movingUnknownMs());

        // No new sensor evidence: grace period must not invent walking or stillness.
        for (long nowMs = 4000; nowMs <= 8000; nowMs += 1000) {
            timeline.advance(
                    nowMs, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        }
        assertEquals(MovementClassifier.Kind.UNKNOWN, timeline.visible());
        assertEquals(0, timeline.walkMs());
        assertEquals(0, timeline.runMs());
        assertEquals(0, timeline.stillMs());
        assertEquals(5000, timeline.unknownMs());
        assertConserved(timeline, 8000);
    }

    @Test
    public void staleCallbackIsIgnoredAndWorkerGapIsUnknown() {
        MotionTimeline timeline = new MotionTimeline();
        timeline.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        timeline.advance(
                1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        timeline.advance(
                2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(2000, timeline.movingUnknownMs());

        timeline.advance(
                1500, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.STILL);
        assertEquals(2000, timeline.movingUnknownMs());
        assertEquals(0, timeline.stillMs());

        // The first callback after a long worker gap cannot classify the gap.
        timeline.advance(
                12000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.UNKNOWN, timeline.visible());
        assertEquals(10000, timeline.unknownMs());
        assertEquals(0, timeline.runMs());
        assertTrue(timeline.unresolvedMs() == 0);
        assertConserved(timeline, 12000);
    }
}
