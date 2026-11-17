package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import it.example.passolibero.MovementClassifier;
import org.junit.Test;

/** P4b/#25: parity with the confirmed MOVING interval and current-gait legacy branch. */
public final class ConfirmedMovingIntervalTransitionTest {
    @Test
    public void keepsPastPendingAndCurrentIntervalSeparateForAllLegacyGaits() {
        long[] pendingMsValues = {0L, 1L, 999L, 5_000L, Long.MAX_VALUE};
        long[] deltaMsValues = {0L, 1L, 1_000L, 5_001L, Long.MAX_VALUE};
        for (MovementClassifier.Kind activity : MovementClassifier.Kind.values()) {
            boolean legacyReliable =
                    activity == MovementClassifier.Kind.RUN
                            || activity == MovementClassifier.Kind.WALK;
            for (long pendingMs : pendingMsValues) {
                for (long deltaMs : deltaMsValues) {
                    ConfirmedMovingIntervalTransition.Effect effect =
                            ConfirmedMovingIntervalTransition.evaluate(
                                    pendingMs, deltaMs, activity.name());
                    assertEquals(activity.name(), pendingMs, effect.previousCreditMs());
                    assertEquals(activity.name(), deltaMs, effect.currentCreditMs());
                    assertEquals(activity.name(), legacyReliable, effect.useCurrentGait());
                }
            }
        }
    }

    @Test
    public void missingAndUntrustedGaitNeverCreatesRunOrWalk() {
        for (String gait : new String[] {null, "", "run", "RUNNING", "UNKNOWN", "STILL"}) {
            ConfirmedMovingIntervalTransition.Effect effect =
                    ConfirmedMovingIntervalTransition.evaluate(2_000L, 1_000L, gait);
            assertFalse(effect.useCurrentGait());
            assertEquals(2_000L, effect.previousCreditMs());
            assertEquals(1_000L, effect.currentCreditMs());
        }
        assertTrue(ConfirmedMovingIntervalTransition.evaluate(0L, 1_000L, "RUN").useCurrentGait());
        assertTrue(ConfirmedMovingIntervalTransition.evaluate(0L, 1_000L, "WALK").useCurrentGait());
    }

    @Test
    public void independentCallbacksNeverCarryPriorGaitOrPendingTime() {
        ConfirmedMovingIntervalTransition.Effect run =
                ConfirmedMovingIntervalTransition.evaluate(1_000L, 500L, "RUN");
        ConfirmedMovingIntervalTransition.Effect missing =
                ConfirmedMovingIntervalTransition.evaluate(0L, 250L, null);
        ConfirmedMovingIntervalTransition.Effect walk =
                ConfirmedMovingIntervalTransition.evaluate(2_000L, 750L, "WALK");

        assertEquals(1_000L, run.previousCreditMs());
        assertEquals(500L, run.currentCreditMs());
        assertTrue(run.useCurrentGait());
        assertEquals(0L, missing.previousCreditMs());
        assertEquals(250L, missing.currentCreditMs());
        assertFalse(missing.useCurrentGait());
        assertEquals(2_000L, walk.previousCreditMs());
        assertEquals(750L, walk.currentCreditMs());
        assertTrue(walk.useCurrentGait());
    }
}
