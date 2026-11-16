package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * ADR-002/#25 G1: characterize which evidence enters the legacy pending-STOP branch before
 * extracting the pure STILL-evidence decision. No production tracking behavior is changed.
 *
 * <p>All callbacks use synthetic monotonic timestamps; this is not a test of GPS/step hardware,
 * Android lifecycle, SQLite, process death, or a physical Android 11 device.
 */
public final class TrackingStillEvidenceBranchCharacterizationTest {
    private static MotionTimeline newLedger() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        return ledger;
    }

    private static void advance(
            MotionTimeline ledger, long nowMs, MotionEvidencePolicy.Evidence evidence) {
        ledger.advance(nowMs, evidence, MovementClassifier.Kind.UNKNOWN);
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
    public void uncertaintyInterruptsUnconfirmedStopAndNewStillEvidenceStartsFreshCandidate() {
        MotionTimeline ledger = newLedger();
        advance(ledger, 1000, MotionEvidencePolicy.Evidence.STILL_STRONG);
        advance(ledger, 2000, MotionEvidencePolicy.Evidence.STILL_STRONG);
        advance(ledger, 3000, MotionEvidencePolicy.Evidence.UNCERTAIN);

        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(2000, ledger.unknownMs());
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(0, ledger.stillMs());

        for (long nowMs = 4000; nowMs <= 7000; nowMs += 1000) {
            advance(ledger, nowMs, MotionEvidencePolicy.Evidence.STILL_STRONG);
        }
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());
        assertEquals(3000, ledger.unknownMs());
        assertConserved(ledger, 7000);
    }

    @Test
    public void strongAndModerateEvidenceSharePendingStopWithCurrentStrengthAtConfirmation() {
        MotionTimeline ledger = newLedger();
        advance(ledger, 1000, MotionEvidencePolicy.Evidence.STILL_MODERATE);
        advance(ledger, 2000, MotionEvidencePolicy.Evidence.STILL_STRONG);
        advance(ledger, 3000, MotionEvidencePolicy.Evidence.STILL_MODERATE);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(3000, ledger.unresolvedMs());

        advance(ledger, 4000, MotionEvidencePolicy.Evidence.STILL_STRONG);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 4000);
    }

    @Test
    public void movingEvidenceAbortsPendingStopWithoutInventingStillness() {
        MotionTimeline ledger = newLedger();
        for (long nowMs = 1000; nowMs <= 3000; nowMs += 1000) {
            advance(ledger, nowMs, MotionEvidencePolicy.Evidence.STILL_STRONG);
        }
        ledger.advance(4000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(3000, ledger.unknownMs());
        assertEquals(0, ledger.stillMs());

        ledger.advance(5000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(2000, ledger.walkMs());
        assertEquals(3000, ledger.unknownMs());
        assertEquals(0, ledger.stillMs());
        assertConserved(ledger, 5000);
    }

    @Test
    public void shortUncertaintyDuringConfirmedStopIsCreditedWhenStillEvidenceReturns() {
        MotionTimeline ledger = newLedger();
        for (long nowMs = 1000; nowMs <= 4000; nowMs += 1000) {
            advance(ledger, nowMs, MotionEvidencePolicy.Evidence.STILL_STRONG);
        }
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());

        advance(ledger, 5000, MotionEvidencePolicy.Evidence.UNCERTAIN);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(1000, ledger.unresolvedMs());

        advance(ledger, 6000, MotionEvidencePolicy.Evidence.STILL_MODERATE);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(6000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 6000);
    }

    @Test
    public void nullEvidenceCannotConfirmPendingStillness() {
        MotionTimeline ledger = newLedger();
        advance(ledger, 1000, MotionEvidencePolicy.Evidence.STILL_STRONG);
        advance(ledger, 2000, null);

        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(0, ledger.stillMs());
        assertEquals(1000, ledger.unknownMs());
        assertConserved(ledger, 2000);
        assertEquals(2000, ledger.unknownMs());
    }
}
