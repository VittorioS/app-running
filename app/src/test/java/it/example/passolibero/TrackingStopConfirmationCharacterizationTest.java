package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * ADR-002/#25 G1: characterize the pre-refactor stop-confirmation transition in MotionTimeline.
 *
 * <p>The synthetic callbacks use monotonic milliseconds and do not simulate Android lifecycle,
 * GPS hardware, process death or SQLite. No production tracking rules are changed here.
 */
public final class TrackingStopConfirmationCharacterizationTest {
    private static MotionTimeline freshLedger() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        return ledger;
    }

    private static MotionTimeline confirmedRun() {
        MotionTimeline ledger = freshLedger();
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
    public void strongStopConfirmsAt4000MsButNotAt3999Ms() {
        MotionTimeline ledger = freshLedger();
        for (long nowMs : new long[] {1000, 2000, 3000, 3999}) {
            ledger.advance(
                    nowMs,
                    MotionEvidencePolicy.Evidence.STILL_STRONG,
                    MovementClassifier.Kind.UNKNOWN);
        }
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(3999, ledger.unresolvedMs());
        assertEquals(0, ledger.stillMs());

        ledger.advance(
                4000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void moderateStopConfirmsAt8000MsButNotAt7999Ms() {
        MotionTimeline ledger = freshLedger();
        for (long nowMs : new long[] {1000, 2000, 3000, 4000, 5000, 6000, 7000, 7999}) {
            ledger.advance(
                    nowMs,
                    MotionEvidencePolicy.Evidence.STILL_MODERATE,
                    MovementClassifier.Kind.UNKNOWN);
        }
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(7999, ledger.unresolvedMs());
        assertEquals(0, ledger.stillMs());

        ledger.advance(
                8000, MotionEvidencePolicy.Evidence.STILL_MODERATE, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(8000, ledger.stillMs());
        assertConserved(ledger, 8000);
    }

    @Test
    public void delayedStepDeliveryBudgetExtendsStrongStopTo12000Ms() {
        MotionTimeline ledger = freshLedger();
        for (long nowMs = 1000; nowMs <= 11000; nowMs += 1000) {
            ledger.advance(
                    nowMs,
                    MotionEvidencePolicy.Evidence.STILL_STRONG,
                    MovementClassifier.Kind.UNKNOWN,
                    StepCounterEvidence.STOP_CONFIRM_MS);
        }
        ledger.advance(
                11999,
                MotionEvidencePolicy.Evidence.STILL_STRONG,
                MovementClassifier.Kind.UNKNOWN,
                StepCounterEvidence.STOP_CONFIRM_MS);
        assertEquals(12000, StepCounterEvidence.STOP_CONFIRM_MS);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(11999, ledger.unresolvedMs());
        assertEquals(0, ledger.stillMs());

        ledger.advance(
                12000,
                MotionEvidencePolicy.Evidence.STILL_STRONG,
                MovementClassifier.Kind.UNKNOWN,
                StepCounterEvidence.STOP_CONFIRM_MS);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(12000, ledger.stillMs());
        assertConserved(ledger, 12000);
    }

    @Test
    public void latestEvidenceStrengthDeterminesThresholdWithoutResettingStopStart() {
        MotionTimeline ledger = freshLedger();
        for (long nowMs = 1000; nowMs <= 3000; nowMs += 1000) {
            ledger.advance(
                    nowMs,
                    MotionEvidencePolicy.Evidence.STILL_MODERATE,
                    MovementClassifier.Kind.UNKNOWN);
        }
        assertEquals(3000, ledger.unresolvedMs());
        ledger.advance(
                4000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void confirmedRunningMicrostopRevertedByMovementStaysRunning() {
        MotionTimeline ledger = confirmedRun();
        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                3999, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(2000, ledger.runMs());
        assertEquals(1999, ledger.unresolvedMs());

        ledger.advance(4000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(4000, ledger.runMs());
        assertEquals(0, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void delayedStepOverlappingPendingStopRevokesItToUnknown() {
        MotionTimeline ledger = confirmedRun();
        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                4000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        ledger.observeDelayedStep(1999);
        assertEquals(2000, ledger.unresolvedMs());
        assertEquals(0, ledger.unknownMs());

        ledger.observeDelayedStep(2000);
        assertEquals(0, ledger.unresolvedMs());
        assertEquals(2000, ledger.unknownMs());
        assertEquals(0, ledger.stillMs());

        ledger.advance(5000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(3000, ledger.runMs());
        assertEquals(2000, ledger.unknownMs());
        assertConserved(ledger, 5000);
    }

    @Test
    public void workerBlackoutCannotConfirmPendingStopRetroactively() {
        MotionTimeline ledger = freshLedger();
        ledger.advance(
                1000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                2000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        assertEquals(2000, ledger.unresolvedMs());

        ledger.advance(
                8001, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.STILL);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(0, ledger.stillMs());
        assertEquals(8001, ledger.unknownMs());
        assertConserved(ledger, 8001);
    }

    @Test
    public void confirmedStopFromRunDoesNotRewritePreviouslyCreditedRunTime() {
        MotionTimeline ledger = confirmedRun();
        for (long nowMs = 3000; nowMs <= 6000; nowMs += 1000) {
            ledger.advance(
                    nowMs,
                    MotionEvidencePolicy.Evidence.STILL_STRONG,
                    MovementClassifier.Kind.UNKNOWN);
        }
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(2000, ledger.runMs());
        assertEquals(4000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 6000);
    }
}
