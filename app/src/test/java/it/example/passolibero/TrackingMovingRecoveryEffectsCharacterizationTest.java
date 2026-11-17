package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * ADR-002/#25 G1: characterize MOVING recovery after pending uncertainty and interrupted stops.
 *
 * <p>Only synthetic monotonic callbacks are used. This is not a device, sensor, GPS, process-death,
 * persistence, or Android lifecycle test. Production behavior is not changed by these tests.
 */
public final class TrackingMovingRecoveryEffectsCharacterizationTest {
    private static MotionTimeline newLedger() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        return ledger;
    }

    private static void moving(
            MotionTimeline ledger, long atMs, MovementClassifier.Kind gait) {
        ledger.advance(atMs, MotionEvidencePolicy.Evidence.MOVING, gait);
    }

    private static void uncertain(MotionTimeline ledger, long atMs) {
        ledger.advance(
                atMs, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
    }

    private static void still(MotionTimeline ledger, long atMs) {
        ledger.advance(
                atMs, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
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
    public void confirmedRunRecoversBriefUncertaintyAsRunBeforeChangingToWalk() {
        MotionTimeline ledger = newLedger();
        moving(ledger, 1000, MovementClassifier.Kind.RUN);
        moving(ledger, 2000, MovementClassifier.Kind.RUN);
        uncertain(ledger, 3000);
        uncertain(ledger, 4000);

        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(2000, ledger.unresolvedMs());
        assertEquals(2000, ledger.runMs());

        moving(ledger, 5000, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(4000, ledger.runMs());
        assertEquals(1000, ledger.walkMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 5000);
    }

    @Test
    public void movementAfterUnconfirmedUncertaintyDoesNotRetroactivelyCreateRun() {
        MotionTimeline ledger = newLedger();
        uncertain(ledger, 1000);
        uncertain(ledger, 2000);
        uncertain(ledger, 3000);
        moving(ledger, 4000, MovementClassifier.Kind.RUN);

        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(3000, ledger.unknownMs());
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(0, ledger.runMs());

        moving(ledger, 5000, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(2000, ledger.runMs());
        assertEquals(3000, ledger.unknownMs());
        assertConserved(ledger, 5000);
    }

    @Test
    public void interruptedStopBecomesUnknownButShortUncertaintyRecoversPreviousWalk() {
        MotionTimeline ledger = newLedger();
        moving(ledger, 1000, MovementClassifier.Kind.WALK);
        moving(ledger, 2000, MovementClassifier.Kind.WALK);
        still(ledger, 3000);
        uncertain(ledger, 4000);

        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(1000, ledger.unknownMs());
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(2000, ledger.walkMs());

        moving(ledger, 5000, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(4000, ledger.walkMs());
        assertEquals(1000, ledger.unknownMs());
        assertEquals(0, ledger.stillMs());
        assertConserved(ledger, 5000);
    }

    @Test
    public void movementAfterConfirmedStillAndUncertaintyRequiresFreshConfirmation() {
        MotionTimeline ledger = newLedger();
        for (long atMs = 1000; atMs <= 4000; atMs += 1000) {
            still(ledger, atMs);
        }
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());

        uncertain(ledger, 5000);
        moving(ledger, 6000, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(1000, ledger.unknownMs());
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(0, ledger.runMs());

        moving(ledger, 7000, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(4000, ledger.stillMs());
        assertEquals(1000, ledger.unknownMs());
        assertEquals(2000, ledger.runMs());
        assertConserved(ledger, 7000);
    }

    @Test
    public void duplicateAndStaleMovementCallbacksCannotChangeRecoveredGait() {
        MotionTimeline ledger = newLedger();
        moving(ledger, 1000, MovementClassifier.Kind.RUN);
        moving(ledger, 2000, MovementClassifier.Kind.RUN);
        uncertain(ledger, 3000);

        moving(ledger, 3000, MovementClassifier.Kind.WALK);
        moving(ledger, 2999, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(0, ledger.walkMs());

        moving(ledger, 4000, MovementClassifier.Kind.RUN);
        assertEquals(MovementClassifier.Kind.RUN, ledger.visible());
        assertEquals(4000, ledger.runMs());
        assertEquals(0, ledger.walkMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 4000);
    }
}
