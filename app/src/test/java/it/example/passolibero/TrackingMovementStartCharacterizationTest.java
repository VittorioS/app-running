package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * ADR-002/#25 G1: characterize the legacy MotionTimeline start confirmation before extracting its
 * movement-start decision. Synthetic monotonic timestamps only; no Android callbacks or GPS.
 */
public final class TrackingMovementStartCharacterizationTest {
    private static MotionTimeline freshLedger() {
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
    }

    @Test
    public void movingRemainsPendingAt1999MsAndConfirmsAtExactly2000Ms() {
        MotionTimeline ledger = freshLedger();
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(1999, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(1999, ledger.unresolvedMs());
        assertEquals(0, ledger.runMs());
        assertEquals(0, ledger.movingMs());

        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(2000, ledger.runMs());
        assertEquals(0, ledger.unresolvedMs());
        assertConserved(ledger, 2000);
    }

    @Test
    public void gaitAtConfirmationDeterminesCreditedCategoryNotEarlierCandidate() {
        MotionTimeline ledger = freshLedger();
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(
                2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(2000, ledger.movingUnknownMs());
        assertEquals(0, ledger.runMs());

        ledger.advance(3000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(2000, ledger.movingUnknownMs());
        assertEquals(1000, ledger.walkMs());
        assertEquals(0, ledger.runMs());
        assertConserved(ledger, 3000);
    }

    @Test
    public void sessionEndingBeforeConfirmationRecordsPendingStartAsUnknown() {
        MotionTimeline ledger = freshLedger();
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(1999, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(1999, ledger.unresolvedMs());

        assertConserved(ledger, 1999);
        assertEquals(1999, ledger.unknownMs());
        assertEquals(0, ledger.runMs());
        assertEquals(0, ledger.movingMs());
        assertEquals(0, ledger.unresolvedMs());
    }

    @Test
    public void uncertaintyInterruptsPendingStartAndRequiresFreshConfirmation() {
        MotionTimeline ledger = freshLedger();
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(
                1500, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(3000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);

        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(1500, ledger.unknownMs());
        assertEquals(1500, ledger.unresolvedMs());
        assertEquals(0, ledger.runMs());

        ledger.advance(3500, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(1500, ledger.unknownMs());
        assertEquals(2000, ledger.runMs());
        assertConserved(ledger, 3500);
    }

    @Test
    public void confirmedStopRemainsCreditedWhileMovementStartIsPending() {
        MotionTimeline ledger = freshLedger();
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
        assertEquals(4000, ledger.stillMs());

        ledger.advance(6000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(4000, ledger.stillMs());
        assertEquals(2000, ledger.runMs());
        assertConserved(ledger, 6000);
    }
}
