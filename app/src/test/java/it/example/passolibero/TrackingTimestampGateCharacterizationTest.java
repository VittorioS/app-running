package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * P4b/#25 G1: characterize the legacy monotonic timestamp gate before extracting it.
 *
 * <p>Only synthetic callbacks are used. No Android clock, sensor or lifecycle is simulated.
 */
public final class TrackingTimestampGateCharacterizationTest {
    @Test
    public void firstCallbackOnlyAnchorsClockWithoutCreditingTime() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(10_000L, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);

        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(0L, ledger.movingMs());
        assertEquals(0L, ledger.stillMs());
        assertEquals(0L, ledger.unknownMs());
        assertEquals(0L, ledger.unresolvedMs());

        ledger.advance(15_001L, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(5_001L, ledger.unknownMs());
        assertEquals(0L, ledger.movingMs());
    }

    @Test
    public void duplicateAndStaleCallbacksCannotMoveTheTimestampOrCreditTwice() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        ledger.advance(2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(2_000L, ledger.runMs());

        ledger.advance(
                2000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                1999, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(3000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);

        assertEquals(2_000L, ledger.runMs());
        assertEquals(1_000L, ledger.walkMs());
        assertEquals(0L, ledger.stillMs());
        assertEquals(0L, ledger.unknownMs());
        ledger.finish();
        assertEquals(3_000L, ledger.movingMs() + ledger.stillMs() + ledger.unknownMs());
    }

    @Test
    public void negativeTimestampKeepsTheOriginalUninitializedSentinelBehavior() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                -500L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(0, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        assertEquals(0L, ledger.runMs());
        assertEquals(0L, ledger.unresolvedMs());

        ledger.advance(
                1000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(1_000L, ledger.unresolvedMs());
        ledger.finish();
        assertEquals(1_000L, ledger.unknownMs());
        assertEquals(0L, ledger.movingMs());
    }
}
