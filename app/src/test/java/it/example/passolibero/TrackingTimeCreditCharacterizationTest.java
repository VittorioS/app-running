package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * ADR-002/#25 P4b G1: characterize legacy time-credit routing before isolating the pure
 * destination-bucket decision from MotionTimeline.credit(). No production code changes.
 *
 * <p>All timestamps and activities are synthetic. This does not emulate Android callbacks,
 * device sensors, lifecycle, recovery or SQLite.
 */
public final class TrackingTimeCreditCharacterizationTest {
    private static MotionTimeline freshLedger() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        return ledger;
    }

    private static MotionTimeline confirmedMovement(MovementClassifier.Kind activity) {
        MotionTimeline ledger = freshLedger();
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, activity);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, activity);
        assertEquals(activity, ledger.visible());
        return ledger;
    }

    private static void assertConserved(MotionTimeline ledger, long elapsedMs) {
        ledger.finish();
        assertEquals(
                ledger.runMs() + ledger.walkMs() + ledger.movingUnknownMs(),
                ledger.movingMs());
        assertEquals(elapsedMs, ledger.movingMs() + ledger.stillMs() + ledger.unknownMs());
        assertEquals(0, ledger.unresolvedMs());
    }

    @Test
    public void pendingStopWithoutPriorMotionCreditsUnknownInsteadOfInventingGait() {
        MotionTimeline ledger = freshLedger();
        ledger.advance(
                1000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                2000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        assertEquals(2000, ledger.unresolvedMs());

        ledger.advance(3000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(2000, ledger.unknownMs());
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(0, ledger.stillMs());

        ledger.advance(4000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(2000, ledger.runMs());
        assertEquals(2000, ledger.unknownMs());
        assertEquals(0, ledger.walkMs());
        assertEquals(0, ledger.stillMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void pendingMicrostopCreditsPreviousWalkNotNewRunGait() {
        MotionTimeline ledger = confirmedMovement(MovementClassifier.Kind.WALK);
        assertEquals(2000, ledger.walkMs());

        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        assertEquals(1000, ledger.unresolvedMs());

        ledger.advance(4000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(3000, ledger.walkMs());
        assertEquals(1000, ledger.runMs());
        assertEquals(0, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void pendingMicrostopCreditsPreviousRunNotNewWalkGait() {
        MotionTimeline ledger = confirmedMovement(MovementClassifier.Kind.RUN);
        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.STILL_MODERATE, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(4000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);

        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(3000, ledger.runMs());
        assertEquals(1000, ledger.walkMs());
        assertEquals(0, ledger.movingUnknownMs());
        assertEquals(0, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void pendingMicrostopCreditsPreviouslyConfirmedMovingUnknownSeparatelyFromWalk() {
        MotionTimeline ledger = freshLedger();
        ledger.advance(
                1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());

        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(4000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);

        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(3000, ledger.movingUnknownMs());
        assertEquals(1000, ledger.walkMs());
        assertEquals(0, ledger.runMs());
        assertEquals(0, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void delayedStepRevocationKeepsPendingStopUnknownOnNextRunCallback() {
        MotionTimeline ledger = confirmedMovement(MovementClassifier.Kind.RUN);
        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                4000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        assertEquals(2000, ledger.unresolvedMs());

        ledger.observeDelayedStep(2000);
        assertEquals(2000, ledger.unknownMs());
        assertEquals(0, ledger.unresolvedMs());

        ledger.advance(5000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(3000, ledger.runMs());
        assertEquals(2000, ledger.unknownMs());
        assertEquals(0, ledger.stillMs());
        assertConserved(ledger, 5000);
    }

    @Test
    public void duplicateAndOutOfOrderCallbacksNeverCreditTheSameIntervalTwice() {
        MotionTimeline ledger = confirmedMovement(MovementClassifier.Kind.RUN);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        ledger.advance(
                1999, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(2000, ledger.runMs());
        assertEquals(0, ledger.walkMs());
        assertEquals(0, ledger.unresolvedMs());

        ledger.advance(3000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(2000, ledger.runMs());
        assertEquals(1000, ledger.walkMs());
        assertEquals(0, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 3000);
    }
}
