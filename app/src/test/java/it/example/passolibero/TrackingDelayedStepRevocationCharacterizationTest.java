package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * ADR-002/#25 G1: characterize the legacy pending-stop revocation caused by delayed steps.
 *
 * <p>Only synthetic monotonic timestamps and cumulative step batches are used. This does not
 * exercise Service callbacks, real hardware, persistence or process recovery.
 */
public final class TrackingDelayedStepRevocationCharacterizationTest {
    private static MotionTimeline confirmedRun() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(2000, ledger.runMs());
        return ledger;
    }

    private static void pendingStrongStop(MotionTimeline ledger, long nowMs) {
        ledger.advance(
                nowMs, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
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
    public void stepJustBeforePendingStartDoesNothingButStepExactlyAtStartRevokes() {
        MotionTimeline ledger = confirmedRun();
        pendingStrongStop(ledger, 3000);
        assertEquals(1000, ledger.unresolvedMs());

        ledger.observeDelayedStep(1999);
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(0, ledger.unknownMs());
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());

        ledger.observeDelayedStep(2000);
        assertEquals(0, ledger.unresolvedMs());
        assertEquals(1000, ledger.unknownMs());
        assertEquals(0, ledger.stillMs());
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());

        ledger.advance(4000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(3000, ledger.runMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void repeatedOrOlderBatchCannotRevokeAnUnrelatedNewPendingStop() {
        MotionTimeline ledger = confirmedRun();
        pendingStrongStop(ledger, 3000);
        pendingStrongStop(ledger, 4000);
        ledger.observeDelayedStep(2000);
        assertEquals(2000, ledger.unknownMs());
        assertEquals(0, ledger.unresolvedMs());

        ledger.observeDelayedStep(2000);
        assertEquals(2000, ledger.unknownMs());
        pendingStrongStop(ledger, 5000);
        assertEquals(1000, ledger.unresolvedMs());

        // The second pending interval started at 4000, not 2000.
        ledger.observeDelayedStep(3000);
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(2000, ledger.unknownMs());
        ledger.observeDelayedStep(4000);
        assertEquals(0, ledger.unresolvedMs());
        assertEquals(3000, ledger.unknownMs());

        ledger.advance(6000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(3000, ledger.runMs());
        assertConserved(ledger, 6000);
    }

    @Test
    public void stepCannotUndoAlreadyConfirmedStillness() {
        MotionTimeline ledger = confirmedRun();
        for (long nowMs = 3000; nowMs <= 6000; nowMs += 1000) {
            pendingStrongStop(ledger, nowMs);
        }
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());
        assertEquals(0, ledger.unresolvedMs());

        ledger.observeDelayedStep(2000);
        ledger.observeDelayedStep(6000);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());

        pendingStrongStop(ledger, 7000);
        assertEquals(5000, ledger.stillMs());
        assertConserved(ledger, 7000);
    }

    @Test
    public void stepArrivingAfterWorkerGapCannotRetroactivelyChangeUnknownLedger() {
        MotionTimeline ledger = confirmedRun();
        pendingStrongStop(ledger, 3000);
        pendingStrongStop(ledger, 4000);

        ledger.advance(
                10001, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.STILL);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(8001, ledger.unknownMs());
        assertEquals(0, ledger.stillMs());

        ledger.observeDelayedStep(3000);
        assertEquals(8001, ledger.unknownMs());
        assertEquals(0, ledger.unresolvedMs());
        assertConserved(ledger, 10001);
    }

    @Test
    public void cumulativeCounterBatchCanRevokePendingStopWithoutInventingPastRun() {
        StepCounterEvidence counter = new StepCounterEvidence();
        assertEquals(0, counter.observe(0, 100, 0));

        MotionTimeline ledger = confirmedRun();
        for (long nowMs = 3000; nowMs <= 11000; nowMs += 1000) {
            ledger.advance(
                    nowMs,
                    MotionEvidencePolicy.Evidence.STILL_STRONG,
                    MovementClassifier.Kind.UNKNOWN,
                    StepCounterEvidence.STOP_CONFIRM_MS);
        }
        assertEquals(0, ledger.stillMs());
        assertEquals(9000, ledger.unresolvedMs());

        assertEquals(10, counter.observe(3000, 110, 11000));
        assertFalse(counter.progressRecent(11000));
        assertTrue(counter.canAmendPendingStop(11000));
        ledger.observeDelayedStep(counter.lastStepMs());
        assertEquals(9000, ledger.unknownMs());
        assertEquals(0, ledger.unresolvedMs());
        assertEquals(2000, ledger.runMs());

        ledger.advance(12000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(3000, ledger.runMs());
        assertEquals(0, ledger.walkMs());
        assertEquals(0, ledger.stillMs());
        assertConserved(ledger, 12000);
    }

    @Test
    public void uncertaintyAlreadySettlesPendingStopBeforeLateStepArrives() {
        MotionTimeline ledger = confirmedRun();
        pendingStrongStop(ledger, 3000);
        ledger.advance(
                4000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(1000, ledger.unknownMs());
        assertEquals(1000, ledger.unresolvedMs());

        ledger.observeDelayedStep(3000);
        assertEquals(1000, ledger.unknownMs());
        assertEquals(1000, ledger.unresolvedMs());

        ledger.advance(5000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(4000, ledger.runMs());
        assertEquals(1000, ledger.unknownMs());
        assertEquals(0, ledger.stillMs());
        assertConserved(ledger, 5000);
    }
}
