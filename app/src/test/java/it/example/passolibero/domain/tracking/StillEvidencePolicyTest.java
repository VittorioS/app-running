package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import it.example.passolibero.MotionEvidencePolicy;
import org.junit.Test;

/** P4b/#25: exact parity with the previous legacy STILL-branch predicate. */
public final class StillEvidencePolicyTest {
    @Test
    public void matchesOriginalPredicateForEveryEvidenceEnumValue() {
        for (MotionEvidencePolicy.Evidence evidence : MotionEvidencePolicy.Evidence.values()) {
            boolean legacy =
                    evidence == MotionEvidencePolicy.Evidence.STILL_STRONG
                            || evidence == MotionEvidencePolicy.Evidence.STILL_MODERATE;
            assertEquals(evidence.name(), legacy, StillEvidencePolicy.isStill(evidence.name()));
        }
    }

    @Test
    public void missingMovingAndUnknownNamesDoNotStartAStop() {
        assertTrue(StillEvidencePolicy.isStill("STILL_STRONG"));
        assertTrue(StillEvidencePolicy.isStill("STILL_MODERATE"));
        assertFalse(StillEvidencePolicy.isStill(null));
        assertFalse(StillEvidencePolicy.isStill("MOVING"));
        assertFalse(StillEvidencePolicy.isStill("UNCERTAIN"));
        assertFalse(StillEvidencePolicy.isStill("still_strong"));
        assertFalse(StillEvidencePolicy.isStill(""));
        assertFalse(StillEvidencePolicy.isStill("UNKNOWN"));
    }
}
