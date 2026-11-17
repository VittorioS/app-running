package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import it.example.passolibero.MotionEvidencePolicy;
import org.junit.Test;

/** P4b/#25: the effects must match the old pending-STOP branch exactly. */
public final class PendingStillTransitionTest {
    @Test
    public void everyCandidateAndThresholdCombinationMatchesTheLegacyBranch() {
        long[] nowMsValues = {0L, 3_999L, 4_000L, 4_999L, 5_000L, 7_999L, 8_000L};
        long[] deltaMsValues = {0L, 1L, 1_000L, Long.MAX_VALUE};
        long[] previousStarts = {-1L, 0L, 500L, Long.MAX_VALUE};
        long[] previousPending = {0L, 999L, Long.MAX_VALUE};
        long[] unresolved = {0L, 2_000L, Long.MAX_VALUE};
        long[] minimumStop = {0L, 5_000L, Long.MAX_VALUE};

        for (MotionEvidencePolicy.Evidence evidence :
                new MotionEvidencePolicy.Evidence[] {
                    MotionEvidencePolicy.Evidence.STILL_STRONG,
                    MotionEvidencePolicy.Evidence.STILL_MODERATE
                }) {
            for (long nowMs : nowMsValues) {
                for (long deltaMs : deltaMsValues) {
                    for (long oldStart : previousStarts) {
                        for (long oldPending : previousPending) {
                            for (long unresolvedMs : unresolved) {
                                for (long minimumMs : minimumStop) {
                                    long legacyStart = oldStart < 0 ? nowMs - deltaMs : oldStart;
                                    long legacyPending = oldPending + deltaMs;
                                    long required =
                                            Math.max(
                                                    evidence
                                                                    == MotionEvidencePolicy.Evidence
                                                                            .STILL_STRONG
                                                            ? 4_000L
                                                            : 8_000L,
                                                    minimumMs);
                                    boolean legacyConfirmed = nowMs - legacyStart >= required;
                                    PendingStillTransition.Effect effect =
                                            PendingStillTransition.evaluate(
                                                    nowMs,
                                                    deltaMs,
                                                    oldStart,
                                                    oldPending,
                                                    unresolvedMs,
                                                    evidence.name(),
                                                    minimumMs);
                                    String context =
                                            evidence.name()
                                                    + " now="
                                                    + nowMs
                                                    + " dt="
                                                    + deltaMs;
                                    assertEquals(context, legacyStart, effect.stopSinceMs());
                                    assertEquals(context, legacyPending, effect.pendingStopMs());
                                    assertEquals(context, legacyConfirmed, effect.confirmed());
                                    assertEquals(
                                            context,
                                            legacyConfirmed ? legacyPending : 0L,
                                            effect.stillCreditMs());
                                    assertEquals(
                                            context,
                                            legacyConfirmed ? unresolvedMs : 0L,
                                            effect.unknownCreditMs());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void confirmsExactlyAtStrongAndModerateThresholds() {
        PendingStillTransition.Effect strongBefore =
                PendingStillTransition.evaluate(
                        3_999L, 1L, 0L, 3_998L, 1_000L, "STILL_STRONG", 0L);
        PendingStillTransition.Effect strongAt =
                PendingStillTransition.evaluate(
                        4_000L, 1L, 0L, 3_999L, 1_000L, "STILL_STRONG", 0L);
        PendingStillTransition.Effect moderateBefore =
                PendingStillTransition.evaluate(
                        7_999L, 1L, 0L, 7_998L, 2_000L, "STILL_MODERATE", 0L);
        PendingStillTransition.Effect moderateAt =
                PendingStillTransition.evaluate(
                        8_000L, 1L, 0L, 7_999L, 2_000L, "STILL_MODERATE", 0L);
        assertFalse(strongBefore.confirmed());
        assertEquals(0L, strongBefore.stillCreditMs());
        assertTrue(strongAt.confirmed());
        assertEquals(4_000L, strongAt.stillCreditMs());
        assertEquals(1_000L, strongAt.unknownCreditMs());
        assertFalse(moderateBefore.confirmed());
        assertTrue(moderateAt.confirmed());
        assertEquals(8_000L, moderateAt.stillCreditMs());
        assertEquals(2_000L, moderateAt.unknownCreditMs());
    }

    @Test
    public void currentEvidenceAndBudgetCanChangeWithoutRestartingTheStop() {
        PendingStillTransition.Effect first =
                PendingStillTransition.evaluate(
                        4_000L, 1_000L, 0L, 3_000L, 900L, "STILL_MODERATE", 0L);
        assertFalse(first.confirmed());
        assertEquals(0L, first.stopSinceMs());
        assertEquals(4_000L, first.pendingStopMs());

        PendingStillTransition.Effect second =
                PendingStillTransition.evaluate(
                        5_000L, 1_000L, first.stopSinceMs(), first.pendingStopMs(), 900L,
                        "STILL_STRONG", 12_000L);
        assertFalse(second.confirmed());
        assertEquals(5_000L, second.pendingStopMs());

        PendingStillTransition.Effect third =
                PendingStillTransition.evaluate(
                        6_000L, 1_000L, second.stopSinceMs(), second.pendingStopMs(), 900L,
                        "STILL_STRONG", 0L);
        assertTrue(third.confirmed());
        assertEquals(6_000L, third.stillCreditMs());
        assertEquals(900L, third.unknownCreditMs());
    }
}
