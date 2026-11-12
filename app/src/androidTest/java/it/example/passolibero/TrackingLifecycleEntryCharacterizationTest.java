package it.example.passolibero;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import it.example.passolibero.domain.SessionRecoveryPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import static org.junit.Assert.*;

/**
 * ADR-002 P4a/#24: pre-extraction characterization of the two legacy lifecycle entry guards.
 * These tests exercise isolated preferences and SQLite, not the real foreground service.
 */
@RunWith(AndroidJUnit4.class)
public final class TrackingLifecycleEntryCharacterizationTest {
    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue24-entry-" + UUID.randomUUID() + "-";

        SandboxContext(Context base) {
            super(base);
        }

        private String isolated(String name) {
            return prefix + name;
        }

        @Override public Context getApplicationContext() {
            return this;
        }

        @Override public File getDatabasePath(String name) {
            return getBaseContext().getDatabasePath(isolated(name));
        }

        @Override public SQLiteDatabase openOrCreateDatabase(
                String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return getBaseContext().openOrCreateDatabase(isolated(name), mode, factory);
        }

        @Override public SQLiteDatabase openOrCreateDatabase(
                String name, int mode, SQLiteDatabase.CursorFactory factory,
                DatabaseErrorHandler errorHandler) {
            return getBaseContext().openOrCreateDatabase(
                    isolated(name), mode, factory, errorHandler);
        }

        @Override public boolean deleteDatabase(String name) {
            return getBaseContext().deleteDatabase(isolated(name));
        }

        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return getBaseContext().getSharedPreferences(isolated(name), mode);
        }

        void cleanUp() {
            getSharedPreferences("passolibero", Context.MODE_PRIVATE)
                    .edit().clear().commit();
            deleteDatabase("sessions.db");
        }
    }

    private static SandboxContext sandbox() {
        return new SandboxContext(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    private static long count(SQLiteDatabase db, String table) {
        return DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM " + table, null);
    }

    // Independent copies of the exact boolean guards in TrackingService before P4a.
    private static boolean legacyShouldRecover(BooleanSupplier active, LongSupplier activeId) {
        return active.getAsBoolean() && activeId.getAsLong() > 0;
    }

    private static boolean legacyShouldLoadForEarlyStop(BooleanSupplier active) {
        return active.getAsBoolean();
    }

    private static boolean legacyShouldRecover(SharedPreferences prefs) {
        return legacyShouldRecover(
                () -> prefs.getBoolean("active", false),
                () -> prefs.getLong("active_id", 0));
    }

    @Test public void startRestoresOnlyWithActiveFlagAndPositiveId() {
        SandboxContext sandbox = sandbox();
        try {
            SharedPreferences prefs = AppPrefs.get(sandbox);
            assertFalse(legacyShouldRecover(prefs));
            prefs.edit().putLong("active_id", 81L).commit();
            assertFalse(legacyShouldRecover(prefs));
            prefs.edit().putBoolean("active", true).putLong("active_id", 0L).commit();
            assertFalse(legacyShouldRecover(prefs));
            prefs.edit().putLong("active_id", -3L).commit();
            assertFalse(legacyShouldRecover(prefs));
            prefs.edit().putLong("active_id", 81L).commit();
            assertTrue(legacyShouldRecover(prefs));
            assertTrue(legacyShouldLoadForEarlyStop(
                    () -> prefs.getBoolean("active", false)));
            prefs.edit().putBoolean("active", false).commit();
            assertFalse(legacyShouldRecover(prefs));
            assertFalse(legacyShouldLoadForEarlyStop(
                    () -> prefs.getBoolean("active", false)));
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void inactiveStartDoesNotReadInvalidStoredIdButActiveStartDoes() {
        SandboxContext sandbox = sandbox();
        try {
            SharedPreferences prefs = AppPrefs.get(sandbox);
            prefs.edit().putBoolean("active", false)
                    .putString("active_id", "invalid legacy value").commit();
            assertFalse(legacyShouldRecover(prefs));
            AtomicInteger idReads = new AtomicInteger();
            assertFalse(legacyShouldRecover(() -> false, () -> {
                idReads.incrementAndGet();
                throw new AssertionError("inactive start must not read active_id");
            }));
            assertEquals(0, idReads.get());

            prefs.edit().putBoolean("active", true).commit();
            try {
                legacyShouldRecover(prefs);
                fail("active start must propagate the existing typed preference error");
            } catch (ClassCastException expected) {
                assertEquals("invalid legacy value", prefs.getString("active_id", null));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void persistedActiveSessionIsReadWithSameIdAndNoNewSqlRow() {
        SandboxContext sandbox = sandbox();
        try {
            SharedPreferences prefs = AppPrefs.get(sandbox);
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(500_000L);
                RunState prior = new RunState();
                prior.id = id;
                prior.startedWallMs = 500_000L;
                prior.startedElapsedMs = 1_000L;
                prior.runMs = 2_000L;
                prior.walkMs = 1_000L;
                prior.unknownMs = 500L;
                prior.distanceM = 130.5;
                prior.movement = "RUN";
                prior.lastLocationValid = true;
                prior.save(prefs, true);

                assertTrue(legacyShouldRecover(prefs));
                RunState restored = RunState.read(prefs);
                assertEquals(id, restored.id);
                assertEquals(130.5, restored.distanceM, 0);
                assertEquals("RUN", restored.movement);
                assertTrue(restored.lastLocationValid);
                assertEquals(1L, count(db.getReadableDatabase(), "sessions"));

                long unobserved = SessionRecoveryPolicy.unobservedUnknownMs(
                        true, 1_000L, 7_000L,
                        restored.runMs, restored.walkMs, restored.movingUnknownMs,
                        restored.stillMs, restored.unknownMs);
                assertEquals(2_500L, unobserved);
                assertEquals(1L, count(db.getReadableDatabase(), "sessions"));
                assertEquals(0L, count(db.getReadableDatabase(), "points"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void invalidActiveIdSelectsFreshSessionAndPreservesExistingHistory() {
        SandboxContext sandbox = sandbox();
        try {
            SharedPreferences prefs = AppPrefs.get(sandbox);
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long oldId = db.newSession(1_000L);
                prefs.edit().putBoolean("active", true).putLong("active_id", 0L)
                        .commit();
                assertFalse(legacyShouldRecover(prefs));
                long freshId = db.newSession(2_000L);
                assertTrue(freshId > oldId);
                assertNotNull(db.session(oldId));
                assertNotNull(db.session(freshId));
                assertEquals(2L, count(db.getReadableDatabase(), "sessions"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void earlyStopLoadsOnlyActiveSnapshotAndKeepsHistoryWhenMarkedInactive() {
        SandboxContext sandbox = sandbox();
        try {
            SharedPreferences prefs = AppPrefs.get(sandbox);
            assertFalse(legacyShouldLoadForEarlyStop(
                    () -> prefs.getBoolean("active", false)));
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(17_000L);
                RunState state = new RunState();
                state.id = id;
                state.startedWallMs = 17_000L;
                state.startedElapsedMs = 1_000L;
                state.distanceM = 44.25;
                state.save(prefs, true);

                assertTrue(legacyShouldLoadForEarlyStop(
                        () -> prefs.getBoolean("active", false)));
                RunState loaded = RunState.read(prefs);
                assertEquals(id, loaded.id);
                // Same persistence operations as the legacy early-STOP path;
                // service dispatch/worker ordering itself is not exercised here.
                db.snapshot(loaded, 4_000L, true);
                loaded.save(prefs, false);

                assertEquals(1L, count(db.getReadableDatabase(), "sessions"));
                assertEquals("COMPLETED", db.session(id).status);
                assertEquals(44.25, db.session(id).distance, 0);
                assertFalse(prefs.getBoolean("active", true));
                assertEquals(0L, prefs.getLong("active_id", -1));
                assertFalse(legacyShouldLoadForEarlyStop(
                        () -> prefs.getBoolean("active", false)));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void recoveryFallbackReadsMonotonicClockLazilyAndKeepsUnknownTime() {
        AtomicInteger elapsedReads = new AtomicInteger();
        AtomicInteger wallReads = new AtomicInteger();
        long[] samples = {20_000L, 20_001L};
        LongSupplier elapsed = () -> samples[elapsedReads.getAndIncrement()];
        LongSupplier wall = () -> {
            wallReads.incrementAndGet();
            return 110_000L;
        };
        // Invalid (<= 0) start short-circuits before reading elapsed for the check.
        long recovered = SessionRecoveryPolicy.recoverStartedElapsedMs(
                -1L, 100_000L, elapsed, wall);
        assertEquals(10_000L, recovered);
        assertEquals(1, elapsedReads.get());
        assertEquals(1, wallReads.get());

        // A future saved start evaluates elapsed for the guard, then reads it again
        // after the wall clock; two monotonic reads are part of the old contract.
        elapsedReads.set(0);
        wallReads.set(0);
        long recoveredFuture = SessionRecoveryPolicy.recoverStartedElapsedMs(
                30_000L, 100_000L, elapsed, wall);
        assertEquals(10_001L, recoveredFuture);
        assertEquals(2, elapsedReads.get());
        assertEquals(1, wallReads.get());

        assertEquals(0L, SessionRecoveryPolicy.unobservedUnknownMs(
                false, 1_000L, 10_000L, 1_000L, 1_000L, 0, 0, 0));
        assertEquals(1_000L, SessionRecoveryPolicy.unobservedUnknownMs(
                true, 1_000L, 5_000L, 1_000L, 1_000L, 500L, 500L, 0));
    }
}
