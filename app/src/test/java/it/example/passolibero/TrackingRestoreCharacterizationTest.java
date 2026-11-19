package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * P4b/#25 G1: characterize the legacy ledger restore contract before extracting its pure
 * counter-normalization rule. All timestamps are synthetic; no Android recovery is exercised.
 */
public final class TrackingRestoreCharacterizationTest {
    @Test
    public void restoreClampsNegativeCountersWithoutReusingAnUnconfirmedStop() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        assertEquals(1000L, ledger.unresolvedMs());

        ledger.restore(-50L, 20L, 30L, -40L, 50L);
        assertEquals(0L, ledger.runMs());
        assertEquals(20L, ledger.walkMs());
        assertEquals(30L, ledger.stillMs());
        assertEquals(0L, ledger.unknownMs());
        assertEquals(50L, ledger.movingUnknownMs());
        assertEquals(0L, ledger.unresolvedMs());
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());

        ledger.finish();
        assertEquals(0L, ledger.unknownMs());
        ledger.advance(5000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(0L, ledger.runMs());
        assertEquals(0L, ledger.unresolvedMs());
    }

    @Test
    public void fourCounterRestoreDropsOldUnknownGaitAndResetsClock() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.restore(1L, 2L, 3L, 4L, 5L);
        ledger.restore(10L, 20L, 30L, 40L);

        assertEquals(10L, ledger.runMs());
        assertEquals(20L, ledger.walkMs());
        assertEquals(30L, ledger.stillMs());
        assertEquals(40L, ledger.unknownMs());
        assertEquals(0L, ledger.movingUnknownMs());
        assertEquals(30L, ledger.movingMs());
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());

        ledger.advance(
                6000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        assertEquals(30L, ledger.stillMs());
        assertEquals(0L, ledger.unresolvedMs());
    }

    @Test
    public void restoreAfterUncertaintyRequiresFreshMovementConfirmation() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(1000L, ledger.unresolvedMs());

        ledger.restore(0L, 2000L, 0L, 0L, 0L);
        ledger.advance(10_000L, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(0L, ledger.unresolvedMs());
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());

        ledger.advance(11_000L, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(1000L, ledger.unresolvedMs());
        assertEquals(0L, ledger.runMs());

        ledger.advance(12_000L, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(2000L, ledger.runMs());
        assertEquals(2000L, ledger.walkMs());
        assertEquals(0L, ledger.unknownMs());
    }
}
