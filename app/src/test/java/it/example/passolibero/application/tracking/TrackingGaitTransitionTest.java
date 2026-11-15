package it.example.passolibero.application.tracking;

import static org.junit.Assert.assertEquals;

import it.example.passolibero.MovementClassifier;
import org.junit.Test;

/**
 * P4b/#25: the pre-refactor TrackingService predicate is the oracle for every pair of visible
 * states. The application decision must not change gait reset timing or behavior.
 */
public final class TrackingGaitTransitionTest {
    @Test
    public void allExistingVisibleStatePairsMatchLegacyServiceResetPredicate() {
        for (MovementClassifier.Kind previous : MovementClassifier.Kind.values()) {
            for (MovementClassifier.Kind current : MovementClassifier.Kind.values()) {
                boolean expected =
                        previous != current
                                && (current == MovementClassifier.Kind.STILL
                                        || current == MovementClassifier.Kind.UNKNOWN);
                assertEquals(
                        previous.name() + " -> " + current.name(),
                        expected,
                        TrackingGaitTransition.shouldInvalidateGait(
                                previous.name(), current.name()));
            }
        }
    }
}
