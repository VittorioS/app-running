package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** P4b/#25 G1: characterize the legacy UNCERTAIN window anchor before extraction. */
public final class TrackingUncertaintyWindowStartCharacterizationTest {
    @Test
    public void nullEvidenceStartsGraceAtPreviousCallbackAndExpiresAtFiveSeconds() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(0L, null, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(1_000L, null, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(4_999L, null, MovementClassifier.Kind.UNKNOWN);

        assertEquals(4_999L, ledger.unresolvedMs());
        assertEquals(0L, ledger.unknownMs());
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());

        ledger.advance(5_000L, null, MovementClassifier.Kind.UNKNOWN);
        assertEquals(0L, ledger.unresolvedMs());
        assertEquals(5_000L, ledger.unknownMs());
        ledger.finish();
        assertEquals(5_000L, ledger.unknownMs());
    }

    @Test
    public void movingRecoveryResetsWindowAndNextUncertaintyGetsItsOwnAnchor() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(1_000L, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(2_000L, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(2_000L, ledger.movingUnknownMs());

        ledger.advance(
                3_000L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(1_000L, ledger.unresolvedMs());

        ledger.advance(4_000L, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(4_000L, ledger.movingUnknownMs());
        assertEquals(0L, ledger.unresolvedMs());

        ledger.advance(
                5_000L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                8_999L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(4_999L, ledger.unresolvedMs());
        assertEquals(0L, ledger.unknownMs());
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());

        ledger.advance(
                9_000L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(0L, ledger.unresolvedMs());
        assertEquals(5_000L, ledger.unknownMs());
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        ledger.finish();
        assertEquals(9_000L, ledger.movingMs() + ledger.stillMs() + ledger.unknownMs());
    }

    @Test
    public void staleCallbacksCannotRestartAnExistingGraceWindow() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                1_000L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                1_000L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                999L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                4_999L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(4_999L, ledger.unresolvedMs());
        ledger.advance(
                5_000L, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(5_000L, ledger.unknownMs());
        assertEquals(0L, ledger.unresolvedMs());
    }
}
