package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import it.example.passolibero.MovementClassifier;
import org.junit.Test;

/** P4b/#25: exact parity with the former MotionTimeline.isMoving(Kind) predicate. */
public final class ConfirmedMotionPolicyTest {
    @Test
    public void allLegacyCommittedStatesRetainOriginalMovementClassification() {
        for (MovementClassifier.Kind committed : MovementClassifier.Kind.values()) {
            boolean legacy =
                    committed == MovementClassifier.Kind.RUN
                            || committed == MovementClassifier.Kind.WALK
                            || committed == MovementClassifier.Kind.MOVING_UNKNOWN;
            assertEquals(
                    committed.name(), legacy, ConfirmedMotionPolicy.isMoving(committed.name()));
        }
    }

    @Test
    public void movingUnknownIsConfirmedMovementWithoutReliableGait() {
        assertTrue(ConfirmedMotionPolicy.isMoving("MOVING_UNKNOWN"));
        assertFalse(ReliableGaitPolicy.hasKnownGait("MOVING_UNKNOWN"));
        assertTrue(ConfirmedMotionPolicy.isMoving("RUN"));
        assertTrue(ConfirmedMotionPolicy.isMoving("WALK"));
        assertFalse(ConfirmedMotionPolicy.isMoving("STILL"));
        assertFalse(ConfirmedMotionPolicy.isMoving("UNKNOWN"));
    }

    @Test
    public void missingOrUnrecognizedCommittedStateRequiresFreshConfirmation() {
        assertFalse(ConfirmedMotionPolicy.isMoving(null));
        assertFalse(ConfirmedMotionPolicy.isMoving(""));
        assertFalse(ConfirmedMotionPolicy.isMoving("run"));
        assertFalse(ConfirmedMotionPolicy.isMoving("WALKING"));
        assertFalse(ConfirmedMotionPolicy.isMoving("MOVING"));
    }
}
