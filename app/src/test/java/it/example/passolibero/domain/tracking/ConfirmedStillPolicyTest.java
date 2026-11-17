package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import it.example.passolibero.MovementClassifier;
import org.junit.Test;

/** P4b/#25: exact parity with the legacy committed == STILL predicate. */
public final class ConfirmedStillPolicyTest {
    @Test
    public void allLegacyCommittedStatesMatchTheOriginalPredicate() {
        for (MovementClassifier.Kind committed : MovementClassifier.Kind.values()) {
            boolean legacy = committed == MovementClassifier.Kind.STILL;
            assertEquals(
                    committed.name(), legacy, ConfirmedStillPolicy.isConfirmed(committed.name()));
        }
    }

    @Test
    public void nullUnknownAndUnrecognizedStatesRequireFreshConfirmation() {
        assertTrue(ConfirmedStillPolicy.isConfirmed("STILL"));
        assertFalse(ConfirmedStillPolicy.isConfirmed(null));
        assertFalse(ConfirmedStillPolicy.isConfirmed("UNKNOWN"));
        assertFalse(ConfirmedStillPolicy.isConfirmed("MOVING_UNKNOWN"));
        assertFalse(ConfirmedStillPolicy.isConfirmed("RUN"));
        assertFalse(ConfirmedStillPolicy.isConfirmed("WALK"));
        assertFalse(ConfirmedStillPolicy.isConfirmed("still"));
        assertFalse(ConfirmedStillPolicy.isConfirmed(""));
    }
}
