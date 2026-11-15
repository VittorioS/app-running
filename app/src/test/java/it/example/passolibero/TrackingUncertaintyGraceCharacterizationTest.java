package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * ADR-002/#25 G1: characterize sustained UNCERTAIN evidence before extracting the
 * unknown-grace expiry decision from the legacy MotionTimeline.
 *
 * <p>This is deterministic JVM characterization, not an Android worker, sensor, or
 * process-death test. In particular, each callback remains within the existing
 * per-callback gap threshold; the cumulative uncertainty boundary is separate.
 */
public final class TrackingUncertaintyGraceCharacterizationTest {
    private static MotionTimeline confirmedUnknownGait() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                1000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        ledger.advance(
                2000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(2000, ledger.movingUnknownMs());
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
    public void uncertaintyExpiresAtExactlyFiveSecondsNotOneMillisecondBefore() {
        MotionTimeline ledger = confirmedUnknownGait();
        for (long nowMs : new long[] {3000, 4000, 5000, 6000, 6999}) {
            ledger.advance(
                    nowMs, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        }

        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(4999, ledger.unresolvedMs());
        assertEquals(0, ledger.unknownMs());
        assertEquals(2000, ledger.movingUnknownMs());

        ledger.advance(
                7000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(5000, ledger.unknownMs());
        assertEquals(0, ledger.unresolvedMs());
        assertEquals(0, ledger.runMs());
        assertEquals(0, ledger.walkMs());
        assertEquals(0, ledger.stillMs());
        assertConserved(ledger, 7000);
    }

    @Test
    public void confirmedMovementAtExpiryRecoversShortUncertaintyWithoutInventingGait() {
        MotionTimeline ledger = confirmedUnknownGait();
        for (long nowMs = 3000; nowMs <= 6000; nowMs += 1000) {
            ledger.advance(
                    nowMs, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        }
        assertEquals(4000, ledger.unresolvedMs());

        // MOVING is handled first. The old ledger credits the unresolved interval
        // to previously confirmed movement instead of expiring it as UNKNOWN.
        ledger.advance(
                7000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.MOVING_UNKNOWN, ledger.visible());
        assertEquals(7000, ledger.movingUnknownMs());
        assertEquals(0, ledger.unknownMs());
        assertEquals(0, ledger.runMs());
        assertEquals(0, ledger.walkMs());
        assertConserved(ledger, 7000);
    }

    @Test
    public void initialUncertaintyNeverEstablishesMovementOrStillness() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        for (long nowMs = 1000; nowMs <= 4000; nowMs += 1000) {
            ledger.advance(
                    nowMs, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        }
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(4000, ledger.unresolvedMs());
        assertEquals(0, ledger.unknownMs());

        ledger.advance(
                5000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(5000, ledger.unknownMs());
        assertEquals(0, ledger.unresolvedMs());
        assertEquals(0, ledger.movingMs());
        assertEquals(0, ledger.stillMs());
        assertConserved(ledger, 5000);
    }

    @Test
    public void unconfirmedStopAndSubsequentUncertaintyRemainUnknown() {
        MotionTimeline ledger = confirmedUnknownGait();
        ledger.advance(
                3000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(0, ledger.stillMs());

        for (long nowMs = 4000; nowMs <= 8000; nowMs += 1000) {
            ledger.advance(
                    nowMs, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        }

        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(6000, ledger.unknownMs());
        assertEquals(2000, ledger.movingUnknownMs());
        assertEquals(0, ledger.stillMs());
        assertEquals(0, ledger.unresolvedMs());
        assertConserved(ledger, 8000);
    }
}
