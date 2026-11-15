package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * ADR-002/#25 G1: characterize the effective STOP confirmation threshold before extracting its
 * pure selection rule. The threshold is recalculated on each current STILL callback.
 *
 * <p>All timestamps are synthetic and monotonic. These tests do not exercise sensors, lifecycle,
 * recovery, actual step delivery or database persistence.
 */
public final class TrackingStopThresholdCharacterizationTest {
    private static MotionTimeline newLedger() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        return ledger;
    }

    private static void still(
            MotionTimeline ledger,
            long nowMs,
            MotionEvidencePolicy.Evidence strength,
            long minimumStopMs) {
        ledger.advance(nowMs, strength, MovementClassifier.Kind.UNKNOWN, minimumStopMs);
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
    public void minimumAboveStrongThresholdConfirmsAtExactFiveSeconds() {
        MotionTimeline ledger = newLedger();
        for (long nowMs = 1000; nowMs <= 4000; nowMs += 1000) {
            still(ledger, nowMs, MotionEvidencePolicy.Evidence.STILL_STRONG, 5000);
        }
        still(ledger, 4999, MotionEvidencePolicy.Evidence.STILL_STRONG, 5000);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(4999, ledger.unresolvedMs());
        assertEquals(0, ledger.stillMs());

        still(ledger, 5000, MotionEvidencePolicy.Evidence.STILL_STRONG, 5000);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(5000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 5000);
    }

    @Test
    public void minimumBelowModerateThresholdCannotShortenEightSecondConfirmation() {
        MotionTimeline ledger = newLedger();
        for (long nowMs = 1000; nowMs <= 7000; nowMs += 1000) {
            still(ledger, nowMs, MotionEvidencePolicy.Evidence.STILL_MODERATE, 3000);
        }
        still(ledger, 7999, MotionEvidencePolicy.Evidence.STILL_MODERATE, 3000);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(7999, ledger.unresolvedMs());

        still(ledger, 8000, MotionEvidencePolicy.Evidence.STILL_MODERATE, 3000);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(8000, ledger.stillMs());
        assertConserved(ledger, 8000);
    }

    @Test
    public void strongToModerateSwitchUsesCurrentStrengthWithoutRestartingPendingStop() {
        MotionTimeline ledger = newLedger();
        for (long nowMs = 1000; nowMs <= 3000; nowMs += 1000) {
            still(ledger, nowMs, MotionEvidencePolicy.Evidence.STILL_STRONG, 0);
        }
        still(ledger, 4000, MotionEvidencePolicy.Evidence.STILL_MODERATE, 0);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(4000, ledger.unresolvedMs());

        still(ledger, 7999, MotionEvidencePolicy.Evidence.STILL_MODERATE, 0);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(7999, ledger.unresolvedMs());

        still(ledger, 8000, MotionEvidencePolicy.Evidence.STILL_MODERATE, 0);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(8000, ledger.stillMs());
        assertConserved(ledger, 8000);
    }

    @Test
    public void relaxingStepDelayBudgetCanConfirmAlreadyPendingStillnessImmediately() {
        MotionTimeline ledger = newLedger();
        for (long nowMs = 1000; nowMs <= 4000; nowMs += 1000) {
            still(ledger, nowMs, MotionEvidencePolicy.Evidence.STILL_STRONG, 12000);
        }
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(4000, ledger.unresolvedMs());

        still(ledger, 5000, MotionEvidencePolicy.Evidence.STILL_STRONG, 0);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(5000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 5000);
    }

    @Test
    public void raisingMinimumBeforeConfirmationDefersStopWithoutResettingItsStart() {
        MotionTimeline ledger = newLedger();
        for (long nowMs = 1000; nowMs <= 3000; nowMs += 1000) {
            still(ledger, nowMs, MotionEvidencePolicy.Evidence.STILL_STRONG, 0);
        }
        still(ledger, 4000, MotionEvidencePolicy.Evidence.STILL_STRONG, 12000);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());

        still(ledger, 8000, MotionEvidencePolicy.Evidence.STILL_STRONG, 12000);
        still(ledger, 11999, MotionEvidencePolicy.Evidence.STILL_STRONG, 12000);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(11999, ledger.unresolvedMs());

        still(ledger, 12000, MotionEvidencePolicy.Evidence.STILL_STRONG, 12000);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(12000, ledger.stillMs());
        assertConserved(ledger, 12000);
    }

    @Test
    public void increasedMinimumCannotUndoAStopAlreadyConfirmed() {
        MotionTimeline ledger = newLedger();
        for (long nowMs = 1000; nowMs <= 4000; nowMs += 1000) {
            still(ledger, nowMs, MotionEvidencePolicy.Evidence.STILL_STRONG, 0);
        }
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());

        still(ledger, 5000, MotionEvidencePolicy.Evidence.STILL_MODERATE, Long.MAX_VALUE);
        still(ledger, 6000, MotionEvidencePolicy.Evidence.STILL_STRONG, Long.MAX_VALUE);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(6000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 6000);
    }
}
