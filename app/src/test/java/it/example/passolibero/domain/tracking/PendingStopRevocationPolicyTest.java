package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** P4b/#25: exact predicate parity with the legacy delayed-step overlap decision. */
public final class PendingStopRevocationPolicyTest {
    @Test
    public void onlyOverlappingStepsCanRevokeAnActivePendingStop() {
        assertFalse(PendingStopRevocationPolicy.shouldRevoke(-1L, 2_000L));
        assertFalse(PendingStopRevocationPolicy.shouldRevoke(2_000L, 1_999L));
        assertTrue(PendingStopRevocationPolicy.shouldRevoke(2_000L, 2_000L));
        assertTrue(PendingStopRevocationPolicy.shouldRevoke(2_000L, 2_001L));
        assertTrue(PendingStopRevocationPolicy.shouldRevoke(0L, 0L));
    }

    @Test
    public void preservesBothLegacyComparisonsForBoundaryAndExtremeLongInputs() {
        long[] stopStarts = {Long.MIN_VALUE, -2L, -1L, 0L, 1L, 2_000L, Long.MAX_VALUE};
        long[] steps = {Long.MIN_VALUE, -2L, -1L, 0L, 1L, 1_999L, 2_000L, Long.MAX_VALUE};
        for (long stopSince : stopStarts) {
            for (long stepMs : steps) {
                assertEquals(
                        "pending start " + stopSince + " step " + stepMs,
                        stopSince >= 0 && stepMs >= stopSince,
                        PendingStopRevocationPolicy.shouldRevoke(stopSince, stepMs));
            }
        }
    }
}
