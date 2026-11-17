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
}
