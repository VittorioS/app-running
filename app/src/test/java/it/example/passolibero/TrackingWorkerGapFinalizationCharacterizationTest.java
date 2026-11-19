package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** P4b/#25 G1: legacy accounting and resets when a worker interval is unobserved. */
public final class TrackingWorkerGapFinalizationCharacterizationTest {
    @Test
    public void callbackAfterLongGapCannotConfirmStillnessOrBackfillMovement() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);

        assertEquals(2000L, ledger.runMs());
        assertEquals(1000L, ledger.unresolvedMs());

        // The first callback after a blackout carries no proof about the elapsed gap.
        ledger.advance(
                8001, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.STILL);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(6001L, ledger.unknownMs());
        assertEquals(0L, ledger.stillMs());
        assertEquals(0L, ledger.unresolvedMs());
        assertEquals(2000L, ledger.runMs());

        // A subsequent finish cannot count the same pending stop again.
        ledger.finish();
        assertEquals(6001L, ledger.unknownMs());
    }

    @Test
    public void exactlyFiveSecondsRemainsObservedForConfirmedMoving() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);

        ledger.advance(
                7000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(7000L, ledger.movingUnknownMs());
        assertEquals(0L, ledger.unknownMs());
        assertEquals(7000L, ledger.movingMs());
    }

    @Test
    public void gapClosesPriorUncertaintyAndRequiresFreshEvidence() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(1000L, ledger.unresolvedMs());

        ledger.advance(
                8001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(6001L, ledger.unknownMs());
        assertEquals(2000L, ledger.runMs());
        assertEquals(0L, ledger.unresolvedMs());

        ledger.advance(
                9001, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(1000L, ledger.unresolvedMs());
        ledger.finish();
        assertEquals(7001L, ledger.unknownMs());
        assertEquals(9001L, ledger.movingMs() + ledger.stillMs() + ledger.unknownMs());
    }
}
