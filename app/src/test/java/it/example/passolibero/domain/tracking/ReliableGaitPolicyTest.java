package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import it.example.passolibero.MovementClassifier;
import org.junit.Test;

/** P4b/#25: every legacy gait label must preserve the original MOVING-branch predicate. */
public final class ReliableGaitPolicyTest {
    @Test
    public void allLegacyActivityKindsMatchExistingLedgerDecision() {
        for (MovementClassifier.Kind activity : MovementClassifier.Kind.values()) {
            boolean original =
                    activity == MovementClassifier.Kind.RUN
                            || activity == MovementClassifier.Kind.WALK;
            assertEquals(
                    activity.name(), original, ReliableGaitPolicy.hasKnownGait(activity.name()));
        }
    }

    @Test
    public void missingAndUnrecognizedActivityPreserveMovingUnknownFallback() {
        assertTrue(ReliableGaitPolicy.hasKnownGait("RUN"));
        assertTrue(ReliableGaitPolicy.hasKnownGait("WALK"));
        assertFalse(ReliableGaitPolicy.hasKnownGait(null));
        assertFalse(ReliableGaitPolicy.hasKnownGait(""));
        assertFalse(ReliableGaitPolicy.hasKnownGait("MOVING_UNKNOWN"));
        assertFalse(ReliableGaitPolicy.hasKnownGait("STILL"));
        assertFalse(ReliableGaitPolicy.hasKnownGait("UNKNOWN"));
        assertFalse(ReliableGaitPolicy.hasKnownGait("run"));
        assertFalse(ReliableGaitPolicy.hasKnownGait("WALKING"));
    }
}
