package it.example.passolibero;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * ADR-002/#25 G1: characterize the legacy branch for an already confirmed STILL state before
 * extracting its pure classification predicate. This PR adds no production behavior changes.
 *
 * <p>All observations use synthetic monotonic callbacks. They do not test Android lifecycle,
 * actual GPS/step delivery, process death, or SQLite persistence.
 */
public final class TrackingConfirmedStillCharacterizationTest {
    private static MotionTimeline confirmedStill() {
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(
                0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        for (long nowMs = 1000; nowMs <= 4000; nowMs += 1000) {
            ledger.advance(
                    nowMs,
                    MotionEvidencePolicy.Evidence.STILL_STRONG,
                    MovementClassifier.Kind.UNKNOWN);
        }
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());
        return ledger;
    }

    private static void still(
            MotionTimeline ledger,
            long nowMs,
            MotionEvidencePolicy.Evidence strength,
            long minimumStopMs) {
        ledger.advance(nowMs, strength, MovementClassifier.Kind.UNKNOWN, minimumStopMs);
    }

    private static void uncertain(MotionTimeline ledger, long nowMs) {
        ledger.advance(
                nowMs, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
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
    public void confirmedStillnessContinuesDespiteStrongerMinimumAndChangedEvidenceStrength() {
        MotionTimeline ledger = confirmedStill();
        still(ledger, 5000, MotionEvidencePolicy.Evidence.STILL_MODERATE, Long.MAX_VALUE);
        still(ledger, 6000, MotionEvidencePolicy.Evidence.STILL_STRONG, Long.MAX_VALUE);
        still(ledger, 7000, MotionEvidencePolicy.Evidence.STILL_MODERATE, Long.MAX_VALUE);

        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(7000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertEquals(0, ledger.unresolvedMs());
        assertConserved(ledger, 7000);
    }

    @Test
    public void briefUncertaintyIsCreditedToConfirmedStillWhenStillnessReturns() {
        MotionTimeline ledger = confirmedStill();
        uncertain(ledger, 5000);
        uncertain(ledger, 6000);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(2000, ledger.unresolvedMs());
        assertEquals(4000, ledger.stillMs());

        still(ledger, 7000, MotionEvidencePolicy.Evidence.STILL_MODERATE, Long.MAX_VALUE);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(7000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 7000);
    }

    @Test
    public void expiredUncertaintyRevokesConfirmedStillAndRequiresNewStopConfirmation() {
        MotionTimeline ledger = confirmedStill();
        for (long nowMs : new long[] {5000, 6000, 7000, 8000, 8999}) {
            uncertain(ledger, nowMs);
        }
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4999, ledger.unresolvedMs());

        uncertain(ledger, 9000);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(5000, ledger.unknownMs());
        assertEquals(4000, ledger.stillMs());

        for (long nowMs : new long[] {10000, 11000, 12000, 12999}) {
            still(ledger, nowMs, MotionEvidencePolicy.Evidence.STILL_STRONG, 0);
        }
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(3999, ledger.unresolvedMs());

        still(ledger, 13000, MotionEvidencePolicy.Evidence.STILL_STRONG, 0);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(8000, ledger.stillMs());
        assertEquals(5000, ledger.unknownMs());
        assertConserved(ledger, 13000);
    }

    @Test
    public void movementFromConfirmedStillMustBeReconfirmedAndMicrostopIsReabsorbed() {
        MotionTimeline ledger = confirmedStill();
        ledger.advance(5000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(1000, ledger.unresolvedMs());
        assertEquals(0, ledger.walkMs());

        ledger.advance(6000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(2000, ledger.walkMs());
        assertEquals(4000, ledger.stillMs());

        still(ledger, 7000, MotionEvidencePolicy.Evidence.STILL_STRONG, 0);
        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(1000, ledger.unresolvedMs());

        ledger.advance(8000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        assertEquals(MovementClassifier.Kind.WALK, ledger.visible());
        assertEquals(4000, ledger.walkMs());
        assertEquals(4000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());
        assertConserved(ledger, 8000);
    }

    @Test
    public void workerBlackoutDoesNotBackfillConfirmedStillOrSkipFreshConfirmation() {
        MotionTimeline ledger = confirmedStill();
        still(ledger, 10001, MotionEvidencePolicy.Evidence.STILL_STRONG, 0);
        assertEquals(MovementClassifier.Kind.UNKNOWN, ledger.visible());
        assertEquals(6001, ledger.unknownMs());
        assertEquals(4000, ledger.stillMs());
        assertEquals(0, ledger.unresolvedMs());

        for (long nowMs : new long[] {11001, 12001, 13001, 14001}) {
            still(ledger, nowMs, MotionEvidencePolicy.Evidence.STILL_STRONG, 0);
        }
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(8000, ledger.stillMs());
        assertEquals(6001, ledger.unknownMs());
        assertConserved(ledger, 14001);
    }

    @Test
    public void delayedStepCannotRevokeAStopThatWasAlreadyConfirmed() {
        MotionTimeline ledger = confirmedStill();
        ledger.observeDelayedStep(3000);
        assertEquals(MovementClassifier.Kind.STILL, ledger.visible());
        assertEquals(4000, ledger.stillMs());
        assertEquals(0, ledger.unknownMs());

        still(ledger, 5000, MotionEvidencePolicy.Evidence.STILL_STRONG, 0);
        assertEquals(5000, ledger.stillMs());
        assertEquals(0, ledger.unresolvedMs());
        assertConserved(ledger, 5000);
    }
}
