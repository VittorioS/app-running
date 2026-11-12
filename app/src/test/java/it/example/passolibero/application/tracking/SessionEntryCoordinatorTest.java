package it.example.passolibero.application.tracking;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.junit.Test;

/** Deterministic parity tests against the original lifecycle-entry boolean expressions. */
public final class SessionEntryCoordinatorTest {
    private static boolean legacyRestore(BooleanSupplier active, LongSupplier id) {
        return active.getAsBoolean() && id.getAsLong() > 0;
    }

    private static boolean legacyEarlyStop(BooleanSupplier active) {
        return active.getAsBoolean();
    }

    @Test
    public void startDecisionMatchesLegacyForBoundaryIds() {
        for (long id : new long[] {-2L, -1L, 0L, 1L, 999_999L}) {
            for (boolean active : new boolean[] {false, true}) {
                assertEquals(
                        legacyRestore(() -> active, () -> id),
                        SessionEntryCoordinator.shouldRestore(() -> active, () -> id));
            }
        }
    }

    @Test
    public void inactiveStartNeverReadsTheSavedId() {
        AtomicInteger activeReads = new AtomicInteger();
        AtomicInteger idReads = new AtomicInteger();
        assertFalse(
                SessionEntryCoordinator.shouldRestore(
                        () -> {
                            activeReads.incrementAndGet();
                            return false;
                        },
                        () -> {
                            idReads.incrementAndGet();
                            throw new AssertionError("inactive start must not read ID");
                        }));
        assertEquals(1, activeReads.get());
        assertEquals(0, idReads.get());
    }

    @Test
    public void activeStartReadsIdExactlyOnceAndKeepsExistingErrors() {
        AtomicInteger activeReads = new AtomicInteger();
        AtomicInteger idReads = new AtomicInteger();
        assertTrue(
                SessionEntryCoordinator.shouldRestore(
                        () -> {
                            activeReads.incrementAndGet();
                            return true;
                        },
                        () -> {
                            idReads.incrementAndGet();
                            return 78L;
                        }));
        assertEquals(1, activeReads.get());
        assertEquals(1, idReads.get());

        IllegalArgumentException invalid = new IllegalArgumentException("bad stored type");
        try {
            SessionEntryCoordinator.shouldRestore(
                    () -> true,
                    () -> {
                        throw invalid;
                    });
            fail("exception from a typed preferences getter must propagate");
        } catch (IllegalArgumentException expected) {
            assertSame(invalid, expected);
        }
    }

    @Test
    public void earlyStopMatchesLegacyForBothMarkersAndNeverNeedsAnId() {
        for (boolean active : new boolean[] {false, true}) {
            AtomicInteger reads = new AtomicInteger();
            assertEquals(
                    legacyEarlyStop(() -> active),
                    SessionEntryCoordinator.shouldHydrateForEarlyStop(
                            () -> {
                                reads.incrementAndGet();
                                return active;
                            }));
            assertEquals(1, reads.get());
        }
    }
}
