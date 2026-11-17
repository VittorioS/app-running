package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import it.example.passolibero.MovementClassifier;
import org.junit.Test;

/** P4b/#25: parity with the legacy unconfirmed-MOVING candidate and its credit decision. */
public final class MovingStartTransitionTest {
    @Test
    public void matchesLegacyCandidateAtEveryClockAndPendingBoundary() {
        long[] nowMsValues = {-1L, 0L, 1_000L, 1_999L, 2_000L, 2_001L, Long.MAX_VALUE};
        long[] deltaMsValues = {1L, 999L, 1_000L, 5_000L, Long.MAX_VALUE};
        long[] movingSinceValues = {-1L, 0L, 500L, 2_000L, Long.MAX_VALUE};
        long[] pendingValues = {0L, 1L, 1_000L, Long.MAX_VALUE};
        for (long nowMs : nowMsValues) {
            for (long deltaMs : deltaMsValues) {
                for (long movingSinceMs : movingSinceValues) {
                    for (long pendingMs : pendingValues) {
                        long legacyMovingSinceMs =
                                movingSinceMs < 0 ? nowMs - deltaMs : movingSinceMs;
                        long legacyPendingMs = pendingMs + deltaMs;
                        boolean legacyConfirmed =
                                MovementStartConfirmationPolicy.isConfirmed(
                                        nowMs - legacyMovingSinceMs);
                        MovingStartTransition.Effect effect =
                                MovingStartTransition.evaluate(
                                        nowMs, deltaMs, movingSinceMs, pendingMs, "RUN");
                        String context =
                                "now=" + nowMs + " dt=" + deltaMs + " since=" + movingSinceMs;
                        assertEquals(context, legacyMovingSinceMs, effect.movingSinceMs());
                        assertEquals(context, legacyPendingMs, effect.pendingMs());
                        assertEquals(context, legacyConfirmed, effect.confirmed());
                        assertEquals(context, legacyConfirmed, effect.useCurrentGait());
                    }
                }
            }
        }
    }

    @Test
    public void onlyReliableGaitAtConfirmationCanDetermineTheNewMovementCategory() {
        for (MovementClassifier.Kind activity : MovementClassifier.Kind.values()) {
            boolean expectedReliable =
                    activity == MovementClassifier.Kind.RUN
                            || activity == MovementClassifier.Kind.WALK;
            MovingStartTransition.Effect effect =
                    MovingStartTransition.evaluate(2_000L, 1_000L, 0L, 1_000L, activity.name());
            assertTrue(activity.name(), effect.confirmed());
            assertEquals(activity.name(), expectedReliable, effect.useCurrentGait());
            assertEquals(activity.name(), 2_000L, effect.pendingMs());
        }
        MovingStartTransition.Effect missing =
                MovingStartTransition.evaluate(2_000L, 1_000L, 0L, 1_000L, null);
        assertTrue(missing.confirmed());
        assertFalse(missing.useCurrentGait());
        assertFalse(
                MovingStartTransition.evaluate(1_999L, 999L, 0L, 1_000L, "RUN").useCurrentGait());
    }

    @Test
    public void callbacksCannotRememberEarlierGaitOrPendingStart() {
        MovingStartTransition.Effect pending =
                MovingStartTransition.evaluate(1_000L, 1_000L, -1L, 0L, "RUN");
        assertFalse(pending.confirmed());
        assertEquals(1_000L, pending.pendingMs());

        MovingStartTransition.Effect confirmed =
                MovingStartTransition.evaluate(
                        2_000L, 1_000L, pending.movingSinceMs(), pending.pendingMs(), "UNKNOWN");
        assertTrue(confirmed.confirmed());
        assertFalse(confirmed.useCurrentGait());
        assertEquals(2_000L, confirmed.pendingMs());

        MovingStartTransition.Effect reset =
                MovingStartTransition.evaluate(5_000L, 1_000L, -1L, 0L, "WALK");
        assertFalse(reset.confirmed());
        assertEquals(4_000L, reset.movingSinceMs());
        assertEquals(1_000L, reset.pendingMs());
    }
}
