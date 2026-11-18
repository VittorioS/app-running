package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** P4b/#25: delayed-step revocation effects must match the legacy ledger branch exactly. */
public final class DelayedStepStopRevocationTransitionTest {
    @Test
    public void everyOverlapBoundaryHasTheLegacyDecisionAndUnknownCredit() {
        long[] starts = {Long.MIN_VALUE, -2L, -1L, 0L, 1L, 2_000L, Long.MAX_VALUE};
        long[] steps = {Long.MIN_VALUE, -2L, -1L, 0L, 1L, 1_999L, 2_000L, Long.MAX_VALUE};
        long[] pending = {0L, 1L, 999L, 9_000L, Long.MAX_VALUE, Long.MIN_VALUE};

        for (long startMs : starts) {
            for (long stepMs : steps) {
                boolean legacyRevoke = startMs >= 0 && stepMs >= startMs;
                for (long pendingMs : pending) {
                    DelayedStepStopRevocationTransition.Effect effect =
                            DelayedStepStopRevocationTransition.evaluate(
                                    startMs, stepMs, pendingMs);
                    assertEquals(legacyRevoke, effect.revokePendingStop());
                    assertEquals(legacyRevoke ? pendingMs : 0L, effect.unknownCreditMs());
                }
            }
        }
    }

    @Test
    public void stepAtStartRevokesButOlderStepAndAlreadySettledStopDoNot() {
        assertFalse(
                DelayedStepStopRevocationTransition.evaluate(2_000L, 1_999L, 100L)
                        .revokePendingStop());
        DelayedStepStopRevocationTransition.Effect overlap =
                DelayedStepStopRevocationTransition.evaluate(2_000L, 2_000L, 1_000L);
        assertTrue(overlap.revokePendingStop());
        assertEquals(1_000L, overlap.unknownCreditMs());
        DelayedStepStopRevocationTransition.Effect settled =
                DelayedStepStopRevocationTransition.evaluate(-1L, 2_000L, 1_000L);
        assertFalse(settled.revokePendingStop());
        assertEquals(0L, settled.unknownCreditMs());
    }

    @Test
    public void independentCallbacksNeverRetainPriorStopOrCredit() {
        DelayedStepStopRevocationTransition.Effect revoked =
                DelayedStepStopRevocationTransition.evaluate(2_000L, 3_000L, 1_000L);
        DelayedStepStopRevocationTransition.Effect none =
                DelayedStepStopRevocationTransition.evaluate(-1L, 3_000L, 5_000L);
        assertEquals(1_000L, revoked.unknownCreditMs());
        assertEquals(0L, none.unknownCreditMs());
        assertFalse(none.revokePendingStop());
    }
}
