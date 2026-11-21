package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;

import it.example.passolibero.MovementClassifier;
import org.junit.Test;

/** P4b/#25: characterize parity with the former recovery branch of MotionTimeline.advance(). */
public final class MovingUncertaintyRecoveryTransitionTest {
    @Test
    public void allLegacyCommittedStatesRoutePendingTimeToTheSameDestination() {
        long[] pendingIntervalsMs = {
            Long.MIN_VALUE, -1L, 0L, 1L, 1_000L, 4_999L, 5_000L, Long.MAX_VALUE
        };
        for (MovementClassifier.Kind committed : MovementClassifier.Kind.values()) {
            boolean wasConfirmedMovement =
                    committed == MovementClassifier.Kind.RUN
                            || committed == MovementClassifier.Kind.WALK
                            || committed == MovementClassifier.Kind.MOVING_UNKNOWN;
            MovingUncertaintyRecoveryTransition.Destination expected =
                    wasConfirmedMovement
                            ? MovingUncertaintyRecoveryTransition.Destination
                                    .PREVIOUS_CONFIRMED_MOVEMENT
                            : MovingUncertaintyRecoveryTransition.Destination.UNKNOWN;
            for (long pendingMs : pendingIntervalsMs) {
                MovingUncertaintyRecoveryTransition.Effect effect =
                        MovingUncertaintyRecoveryTransition.settle(committed.name(), pendingMs);
                assertEquals(committed.name(), expected, effect.destination());
                assertEquals(committed.name(), pendingMs, effect.creditMs());
            }
        }
    }

    @Test
    public void missingAndUnrecognizedStateNeverInventsMovement() {
        for (String stateName : new String[] {null, "", "RUNNING", "run", "MOVING"}) {
            MovingUncertaintyRecoveryTransition.Effect effect =
                    MovingUncertaintyRecoveryTransition.settle(stateName, 2_000L);
            assertEquals(
                    MovingUncertaintyRecoveryTransition.Destination.UNKNOWN, effect.destination());
            assertEquals(2_000L, effect.creditMs());
        }
    }

    @Test
    public void consecutiveCallsDoNotRememberPreviousMovementOrPendingTime() {
        MovingUncertaintyRecoveryTransition.Effect first =
                MovingUncertaintyRecoveryTransition.settle("RUN", 3_000L);
        MovingUncertaintyRecoveryTransition.Effect second =
                MovingUncertaintyRecoveryTransition.settle("STILL", 1_000L);
        MovingUncertaintyRecoveryTransition.Effect third =
                MovingUncertaintyRecoveryTransition.settle("MOVING_UNKNOWN", 500L);

        assertEquals(
                MovingUncertaintyRecoveryTransition.Destination.PREVIOUS_CONFIRMED_MOVEMENT,
                first.destination());
        assertEquals(3_000L, first.creditMs());
        assertEquals(MovingUncertaintyRecoveryTransition.Destination.UNKNOWN, second.destination());
        assertEquals(1_000L, second.creditMs());
        assertEquals(
                MovingUncertaintyRecoveryTransition.Destination.PREVIOUS_CONFIRMED_MOVEMENT,
                third.destination());
        assertEquals(500L, third.creditMs());
    }

    @Test
    public void absentWindowCannotConsumeOrReclassifyStaleUnresolvedTime() {
        for (long uncertainSinceMs : new long[] {Long.MIN_VALUE, -2L, -1L}) {
            for (long pendingMs : new long[] {0L, 1L, 4_999L, Long.MAX_VALUE}) {
                MovingUncertaintyRecoveryTransition.WindowEffect effect =
                        MovingUncertaintyRecoveryTransition.resolveWindow(
                                uncertainSinceMs, "RUN", pendingMs);
                assertEquals(false, effect.recoverPendingWindow());
                assertEquals(0L, effect.creditMs());
                assertEquals(
                        MovingUncertaintyRecoveryTransition.Destination.UNKNOWN,
                        effect.destination());
            }
        }
    }

    @Test
    public void activeWindowPreservesDestinationAndUnclampedDuration() {
        for (long sinceMs : new long[] {0L, 1L, Long.MAX_VALUE}) {
            for (String state :
                    new String[] {null, "RUN", "WALK", "MOVING_UNKNOWN", "STILL", "UNKNOWN"}) {
                for (long pendingMs : new long[] {Long.MIN_VALUE, 0L, 999L, Long.MAX_VALUE}) {
                    MovingUncertaintyRecoveryTransition.Effect legacy =
                            MovingUncertaintyRecoveryTransition.settle(state, pendingMs);
                    MovingUncertaintyRecoveryTransition.WindowEffect effect =
                            MovingUncertaintyRecoveryTransition.resolveWindow(
                                    sinceMs, state, pendingMs);
                    assertEquals(true, effect.recoverPendingWindow());
                    assertEquals(legacy.destination(), effect.destination());
                    assertEquals(pendingMs, effect.creditMs());
                }
            }
        }
    }

    @Test
    public void consecutiveWindowDecisionsNeverRememberOlderActivity() {
        MovingUncertaintyRecoveryTransition.WindowEffect before =
                MovingUncertaintyRecoveryTransition.resolveWindow(10L, "RUN", 1_000L);
        MovingUncertaintyRecoveryTransition.WindowEffect absent =
                MovingUncertaintyRecoveryTransition.resolveWindow(-1L, "RUN", 999L);
        MovingUncertaintyRecoveryTransition.WindowEffect after =
                MovingUncertaintyRecoveryTransition.resolveWindow(20L, "STILL", 250L);

        assertEquals(1_000L, before.creditMs());
        assertEquals(0L, absent.creditMs());
        assertEquals(false, absent.recoverPendingWindow());
        assertEquals(250L, after.creditMs());
        assertEquals(MovingUncertaintyRecoveryTransition.Destination.UNKNOWN, after.destination());
    }
}
