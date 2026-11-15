package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * ADR-002/#25 G1: characterize the ledger distinction between a confirmed moving state and a
 * reliable gait. MOVING_UNKNOWN is confirmed movement but is not a reliable RUN/WALK label.
 *
 * <p>All callbacks are synthetic; this does not test Android hardware, Service or process death.
 */
public final class TrackingConfirmedMotionCharacterizationTest {
    private static MotionTimeline newLedger() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
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
    public void confirmedMovingUnknownResumesAfterBriefUncertaintyWithoutReconfirmation() {
        MotionTimeline ledger = newLedger();
        ledger.advance(
                1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(2000, ledger.movingUnknownMs());

        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(1000, ledger.unresolvedMs());

        ledger.advance(4000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(3000, ledger.movingUnknownMs());
        assertEquals(1000, ledger.walkMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void confirmedStillRequiresFreshMovingConfirmationEvenWithRunGait() {
        MotionTimeline ledger = newLedger();
        for (long nowMs = 1000; nowMs <= 4000; nowMs += 1000) {
            ledger.advance(
                    nowMs,
                    MotionEvidencePolicy.Evidence.STILL_STRONG,
                    MovementClassifier.Kind.UNKNOWN);
        }
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());

        ledger.advance(5000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(0, ledger.runMs());

        ledger.advance(6000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(2000, ledger.runMs());
        assertEquals(4000, ledger.stillMs());
        assertConserved(ledger, 6000);
    }

    @Test
    public void workerBlackoutMakesCommittedStateUnknownAndRestartsConfirmation() {
        MotionTimeline ledger = newLedger();
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());

        ledger.advance(8001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(6001, ledger.unknownMs());

        ledger.advance(9001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(1000, ledger.unresolvedMs());

        ledger.advance(10001, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(4000, ledger.runMs());
        assertEquals(6001, ledger.unknownMs());
        assertConserved(ledger, 10001);
    }
}
