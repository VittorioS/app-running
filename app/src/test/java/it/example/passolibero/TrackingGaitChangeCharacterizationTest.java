package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * ADR-002/#25 G1: characterize how the legacy ledger maps gait when motion is confirmed.
 *
 * <p>Times and evidence are synthetic. These tests do not emulate Service, hardware, SQLite or
 * process recovery. All credited intervals must remain conserved after finish().
 */
public final class TrackingGaitChangeCharacterizationTest {
    private static MotionTimeline newLedger() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        return ledger;
    }

    private static MotionTimeline confirmedRun() {
        MotionTimeline ledger = newLedger();
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(2000, ledger.runMs());
        return ledger;
    }

    private static void assertConserved(MotionTimeline ledger, long elapsedMs) {
        ledger.finish();
        assertEquals(
                ledger.runMs() + ledger.walkMs() + ledger.movingUnknownMs(),
                ledger.movingMs());
        assertEquals(
                elapsedMs, ledger.movingMs() + ledger.stillMs() + ledger.unknownMs());
        assertEquals(0, ledger.unresolvedMs());
    }

    @Test
    public void successiveGaitChangesOnlyCreditEachFreshCallbackInterval() {
        MotionTimeline ledger = newLedger();
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        ledger.advance(3000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(
                4000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                5000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        ledger.advance(6000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);

        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(3000, ledger.walkMs());
        assertEquals(1000, ledger.runMs());
        assertEquals(2000, ledger.movingUnknownMs());
        assertEquals(0, ledger.unknownMs());
        assertEquals(0, ledger.stillMs());
        assertConserved(ledger, 6000);
    }

    @Test
    public void pendingGaitCandidatesNeverReclassifyIntervalsAtConfirmation() {
        MotionTimeline ledger = newLedger();
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(1500, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(1500, ledger.unresolvedMs());

        ledger.advance(
                2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(2000, ledger.movingUnknownMs());
        assertEquals(0, ledger.walkMs());
        assertEquals(0, ledger.runMs());

        ledger.advance(3000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(1000, ledger.walkMs());
        assertEquals(2000, ledger.movingUnknownMs());
        assertConserved(ledger, 3000);
    }

    @Test
    public void nonGaitActivityLabelsUnderMovingEvidenceNeverCreateStillness() {
        MotionTimeline ledger = newLedger();
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.STILL);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.STILL);
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(2000, ledger.movingUnknownMs());

        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                4000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.MOVING_UNKNOWN);
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(4000, ledger.movingUnknownMs());
        assertEquals(0, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void briefUncertaintyCreditsPastRunBeforeCurrentWalkCallback() {
        MotionTimeline ledger = confirmedRun();
        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(2000, ledger.runMs());

        ledger.advance(4000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(3000, ledger.runMs());
        assertEquals(1000, ledger.walkMs());
        assertEquals(0, ledger.movingUnknownMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void gaitAfterConfirmedStopNeedsFreshMotionConfirmation() {
        MotionTimeline ledger = confirmedRun();
        for (long nowMs = 3000; nowMs <= 6000; nowMs += 1000) {
            ledger.advance(
                    nowMs,
                    MotionEvidencePolicy.Evidence.STILL_STRONG,
                    MovementClassifier.Kind.UNKNOWN);
        }
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());

        ledger.advance(
                7000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(1000, ledger.unresolvedMs());

        ledger.advance(
                8000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(2000, ledger.movingUnknownMs());

        ledger.advance(9000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(3000, ledger.runMs());
        assertEquals(4000, ledger.stillMs());
        assertEquals(2000, ledger.movingUnknownMs());
        assertConserved(ledger, 9000);
    }

    @Test
    public void workerGapAndStaleGaitCannotBackfillAnUnobservedInterval() {
        MotionTimeline ledger = confirmedRun();
        ledger.advance(1500, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());

        ledger.advance(8001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(6001, ledger.unknownMs());
        assertEquals(2000, ledger.runMs());

        ledger.advance(9001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        ledger.advance(10001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(2000, ledger.walkMs());
        ledger.advance(11001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(3000, ledger.runMs());
        assertEquals(6001, ledger.unknownMs());
        assertConserved(ledger, 11001);
    }
}
