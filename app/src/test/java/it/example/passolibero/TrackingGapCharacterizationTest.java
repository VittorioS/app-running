package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * P4b/#25 G1 before the unobserved-worker-gap seam: characterize the legacy
 * MotionTimeline.advance timestamp boundary and interval accounting.
 * No Android runtime, GPS source, callbacks, or persistence is simulated here.
 */
public final class TrackingGapCharacterizationTest {
    private static void checkConserved(MotionTimeline ledger, long elapsedMs) {
        ledger.finish();
        assertEquals(
                ledger.runMs() + ledger.walkMs() + ledger.movingUnknownMs(),
                ledger.movingMs());
        assertEquals(
                elapsedMs, ledger.movingMs() + ledger.stillMs() + ledger.unknownMs());
    }

    @Test
    public void exactlyFiveSecondsOfFreshMotionDoesNotBecomeAnUnobservedGap() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                5000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);

        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(5000, ledger.movingUnknownMs());
        assertEquals(0, ledger.unknownMs());
        checkConserved(ledger, 5000);
    }

    @Test
    public void justOverFiveSecondsIsUnknownEvenWhenTheNewFixReportsRunning() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                5001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);

        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(5001, ledger.unknownMs());
        assertEquals(0, ledger.runMs());

        // Only later fresh callbacks can establish a new run, never the gap.
        ledger.advance(
                6001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(
                7001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(
                8001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(5001, ledger.unknownMs());
        assertEquals(3000, ledger.runMs());
        checkConserved(ledger, 8001);
    }

    @Test
    public void unconfirmedStopBeforeGapIsSettledAsUnknownNotStillness() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);

        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(0, ledger.stillMs());
        assertEquals(1000, ledger.unresolvedMs());

        ledger.advance(
                8001, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.STILL);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(0, ledger.stillMs());
        assertEquals(6001, ledger.unknownMs());
        assertEquals(2000, ledger.movingUnknownMs());
        checkConserved(ledger, 8001);
    }

    @Test
    public void staleCallbacksDoNotMoveTheGapReferenceTimestamp() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                999, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.STILL);
        ledger.advance(
                1000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.STILL);
        ledger.advance(
                2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);

        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(0, ledger.unknownMs());
        assertEquals(0, ledger.stillMs());
        checkConserved(ledger, 2000);
    }
}
