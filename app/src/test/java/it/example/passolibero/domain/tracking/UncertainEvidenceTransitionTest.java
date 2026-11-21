package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** P4b/#25: exact parity with the former UNCERTAIN branch of MotionTimeline.advance(). */
public final class UncertainEvidenceTransitionTest {
    @Test
    public void matchesLegacyPendingAndExpiryEffectsAtAllGraceBoundaries() {
        long[] elapsedMsValues = {-1L, 0L, 1L, 4_999L, 5_000L, 5_001L, Long.MAX_VALUE};
        long[] pendingMsValues = {0L, 1L, 4_999L, 10_000L, Long.MAX_VALUE};
        long[] deltaMsValues = {0L, 1L, 999L, 1_000L, 5_000L, Long.MAX_VALUE};
        for (long elapsedMs : elapsedMsValues) {
            for (long pendingMs : pendingMsValues) {
                for (long deltaMs : deltaMsValues) {
                    boolean legacyPending = UncertaintyGracePolicy.shouldKeepPending(elapsedMs);
                    long legacyUnresolvedMs = legacyPending ? pendingMs + deltaMs : 0L;
                    long legacyUnknownCreditMs = legacyPending ? 0L : pendingMs + deltaMs;
                    UncertainEvidenceTransition.Effect effect =
                            UncertainEvidenceTransition.resolve(elapsedMs, pendingMs, deltaMs);
                    String context =
                            "elapsed=" + elapsedMs + " pending=" + pendingMs + " dt=" + deltaMs;
                    assertEquals(context, legacyUnresolvedMs, effect.unresolvedMsAfter());
                    assertEquals(context, legacyUnknownCreditMs, effect.unknownCreditMs());
                    assertEquals(context, !legacyPending, effect.invalidateConfirmedState());
                }
            }
        }
    }

    @Test
    public void uncertainIntervalRemainsPendingUntilExactlyFiveSeconds() {
        UncertainEvidenceTransition.Effect justBefore =
                UncertainEvidenceTransition.resolve(4_999L, 3_999L, 1_000L);
        assertEquals(4_999L, justBefore.unresolvedMsAfter());
        assertEquals(0L, justBefore.unknownCreditMs());
        assertEquals(false, justBefore.invalidateConfirmedState());

        UncertainEvidenceTransition.Effect expired =
                UncertainEvidenceTransition.resolve(5_000L, 4_000L, 1_000L);
        assertEquals(0L, expired.unresolvedMsAfter());
        assertEquals(5_000L, expired.unknownCreditMs());
        assertEquals(true, expired.invalidateConfirmedState());
    }

    @Test
    public void eachDecisionIsStatelessAndOnlyUsesItsCurrentInput() {
        UncertainEvidenceTransition.Effect expired =
                UncertainEvidenceTransition.resolve(9_000L, 4_000L, 1_000L);
        UncertainEvidenceTransition.Effect fresh =
                UncertainEvidenceTransition.resolve(1_000L, 0L, 1_000L);
        assertEquals(5_000L, expired.unknownCreditMs());
        assertEquals(0L, fresh.unknownCreditMs());
        assertEquals(1_000L, fresh.unresolvedMsAfter());
        assertEquals(false, fresh.invalidateConfirmedState());
    }

    @Test
    public void firstUncertaintyAnchorsAtPreviousAcceptedCallback() {
        UncertainEvidenceTransition.WindowEffect effect =
                UncertainEvidenceTransition.resolveWindow(1_000L, 1_000L, -1L, 0L);
        assertEquals(0L, effect.uncertainSinceMsAfter());
        assertEquals(1_000L, effect.unresolvedMsAfter());
        assertEquals(0L, effect.unknownCreditMs());
        assertEquals(false, effect.invalidateConfirmedState());
    }

    @Test
    public void existingAnchorPersistsThroughExactGraceExpiry() {
        UncertainEvidenceTransition.WindowEffect before =
                UncertainEvidenceTransition.resolveWindow(4_999L, 999L, 0L, 4_000L);
        assertEquals(0L, before.uncertainSinceMsAfter());
        assertEquals(4_999L, before.unresolvedMsAfter());
        assertEquals(0L, before.unknownCreditMs());

        UncertainEvidenceTransition.WindowEffect expired =
                UncertainEvidenceTransition.resolveWindow(5_000L, 1L, 0L, 4_999L);
        assertEquals(0L, expired.uncertainSinceMsAfter());
        assertEquals(0L, expired.unresolvedMsAfter());
        assertEquals(5_000L, expired.unknownCreditMs());
        assertEquals(true, expired.invalidateConfirmedState());

        UncertainEvidenceTransition.WindowEffect later =
                UncertainEvidenceTransition.resolveWindow(6_000L, 1_000L, 0L, 0L);
        assertEquals(0L, later.uncertainSinceMsAfter());
        assertEquals(1_000L, later.unknownCreditMs());
    }

    @Test
    public void newWindowAfterRecoveryAndNegativeSentinelKeepLegacyArithmetic() {
        UncertainEvidenceTransition.WindowEffect fresh =
                UncertainEvidenceTransition.resolveWindow(9_000L, 1_000L, -1L, 0L);
        assertEquals(8_000L, fresh.uncertainSinceMsAfter());
        assertEquals(1_000L, fresh.unresolvedMsAfter());
        assertEquals(false, fresh.invalidateConfirmedState());

        UncertainEvidenceTransition.WindowEffect negative =
                UncertainEvidenceTransition.resolveWindow(-1L, 10L, -3L, 5L);
        assertEquals(-11L, negative.uncertainSinceMsAfter());
        assertEquals(15L, negative.unresolvedMsAfter());
        assertEquals(0L, negative.unknownCreditMs());
    }
}
