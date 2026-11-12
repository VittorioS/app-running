package it.example.passolibero.application.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Tests exact action matching and the deliberately conservative restart fallback. */
public final class TrackingCommandClassifierTest {
    @Test
    public void everyExistingCommandHasItsOriginalBranch() {
        assertEquals(
                TrackingCommandClassifier.Command.STOP,
                TrackingCommandClassifier.classify("it.example.passolibero.STOP"));
        assertEquals(
                TrackingCommandClassifier.Command.LABEL_WALK,
                TrackingCommandClassifier.classify("it.example.passolibero.LABEL_WALK"));
        assertEquals(
                TrackingCommandClassifier.Command.LABEL_RUN,
                TrackingCommandClassifier.classify("it.example.passolibero.LABEL_RUN"));
        assertEquals(
                TrackingCommandClassifier.Command.SPRINT_PREPARE_VOICE,
                TrackingCommandClassifier.classify(
                        "it.example.passolibero.SPRINT_PREPARE_VOICE"));
        assertEquals(
                TrackingCommandClassifier.Command.SPRINT_START,
                TrackingCommandClassifier.classify("it.example.passolibero.SPRINT_START"));
        assertEquals(
                TrackingCommandClassifier.Command.SPRINT_CANCEL,
                TrackingCommandClassifier.classify("it.example.passolibero.SPRINT_CANCEL"));
    }

    @Test
    public void explicitStartNullAndUnknownActionsPreserveFallback() {
        for (String action :
                new String[] {
                    null,
                    "",
                    "START",
                    "it.example.passolibero.START",
                    "it.example.passolibero.stop",
                    "it.example.passolibero.STOP ",
                    "it.example.passolibero.OTHER"
                }) {
            assertEquals(
                    TrackingCommandClassifier.Command.START_OR_RESTART,
                    TrackingCommandClassifier.classify(action));
        }
    }
}
