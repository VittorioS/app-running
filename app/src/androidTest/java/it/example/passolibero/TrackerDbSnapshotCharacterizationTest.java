package it.example.passolibero;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.*;

/** ADR-002 P3c/#23: characterize the legacy session snapshot writer before extraction. */
@RunWith(AndroidJUnit4.class)
public final class TrackerDbSnapshotCharacterizationTest {
    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue23-snapshot-" + UUID.randomUUID() + "-";

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

        void cleanUp() {
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

    @Test public void unfinishedSnapshotWritesEveryCounterWithoutOtherRows() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(123456);
                RunState state = new RunState();
                state.id = id;
                state.distanceM = 145.75;
                state.walkM = 45.25;
                state.runM = 100.5;
                state.movingMs = 1001;
                state.walkMs = 1002;
                state.runMs = 1003;
                state.stillMs = 1004;
                state.unknownMs = 1005;
                state.movingUnknownMs = 1006;
                state.steps = 79;
                state.runSteps = 29;
                state.walkSteps = 50;
                state.goalM = 5000;
                state.checkpointInterval = 250;
                state.maxRunSpeedKmh = 16.75;

                db.snapshot(state, 98765L, false);
                TrackerDb.Entry row = db.session(id);
                assertNotNull(row);
                assertEquals(123456L, row.startedAt);
                assertEquals("IN_PROGRESS", row.status);
                assertNull(row.endedAt);
                assertEquals(98765L, row.elapsedMs);
                assertEquals(145.75, row.distance, 0);
                assertEquals(45.25, row.walkM, 0);
                assertEquals(100.5, row.runM, 0);
                assertEquals(1001L, row.movingMs);
                assertEquals(1002L, row.walkMs);
                assertEquals(1003L, row.runMs);
                assertEquals(1004L, row.stillMs);
                assertEquals(1005L, row.unknownMs);
                assertEquals(1006L, row.movingUnknownMs);
                assertEquals(79, row.steps);
                assertEquals(Integer.valueOf(29), row.runSteps);
                assertEquals(Integer.valueOf(50), row.walkSteps);
                assertEquals(5000, row.goalM);
                assertEquals(Integer.valueOf(250), row.checkpointIntervalM);
                assertEquals(Double.valueOf(16.75), row.maxRunSpeedKmh);
                assertEquals(1L, count(db.getReadableDatabase(), "sessions"));
                assertEquals(0L, count(db.getReadableDatabase(), "points"));
                assertEquals(0L, count(db.getReadableDatabase(), "sprints"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void repeatedSnapshotsOverwriteFieldsButDoNotTouchStartedAt() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(100);
                RunState state = new RunState();
                state.id = id;
                state.distanceM = 100;
                state.steps = 10;
                state.runSteps = 5;
                state.walkSteps = 5;
                db.snapshot(state, 4000, false);

                state.distanceM = 7.5;
                state.steps = 3;
                state.runSteps = 1;
                state.walkSteps = 2;
                state.unknownMs = 900;
                state.goalM = 800;
                db.snapshot(state, 2000, false);

                TrackerDb.Entry row = db.session(id);
                assertEquals(100L, row.startedAt);
                assertEquals(2000L, row.elapsedMs);
                assertEquals(7.5, row.distance, 0);
                assertEquals(3, row.steps);
                assertEquals(Integer.valueOf(1), row.runSteps);
                assertEquals(Integer.valueOf(2), row.walkSteps);
                assertEquals(900L, row.unknownMs);
                assertEquals(800, row.goalM);
                assertEquals("IN_PROGRESS", row.status);
                assertNull(row.endedAt);
                assertEquals(1L, count(db.getReadableDatabase(), "sessions"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void finishedSnapshotUsesWallClockAndLaterUnfinishedDoesNotReopen() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(500);
                RunState state = new RunState();
                state.id = id;
                long before = System.currentTimeMillis();
                db.snapshot(state, 6000, true);
                long after = System.currentTimeMillis();
                TrackerDb.Entry finished = db.session(id);
                assertEquals("COMPLETED", finished.status);
                assertNotNull(finished.endedAt);
                assertTrue(finished.endedAt >= before && finished.endedAt <= after);
                long originalEndedAt = finished.endedAt;

                state.distanceM = 91.25;
                db.snapshot(state, 7000, false);
                TrackerDb.Entry later = db.session(id);
                assertEquals("COMPLETED", later.status);
                assertEquals(Long.valueOf(originalEndedAt), later.endedAt);
                assertEquals(91.25, later.distance, 0);
                assertEquals(7000L, later.elapsedMs);
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void invalidMaximumSpeedAndNonpositiveCheckpointBecomeNull() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(1);
                RunState state = new RunState();
                state.id = id;
                double[] invalid = {
                        Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                        0.0, -4.25
                };
                int[] invalidIntervals = {0, -1, -1000, 0, -5};
                for (int i = 0; i < invalid.length; i++) {
                    state.maxRunSpeedKmh = invalid[i];
                    state.checkpointInterval = invalidIntervals[i];
                    db.snapshot(state, i, false);
                    TrackerDb.Entry row = db.session(id);
                    assertNull(row.maxRunSpeedKmh);
                    assertNull(row.checkpointIntervalM);
                }
                state.maxRunSpeedKmh = 0.125;
                state.checkpointInterval = 500;
                db.snapshot(state, 999, false);
                TrackerDb.Entry row = db.session(id);
                assertEquals(Double.valueOf(0.125), row.maxRunSpeedKmh);
                assertEquals(Integer.valueOf(500), row.checkpointIntervalM);
                assertEquals(1L, count(db.getReadableDatabase(), "sessions"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void historicNullableRowIsUpdatedInPlaceWithoutRewritingStartTime() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                SQLiteDatabase sql = db.getWritableDatabase();
                sql.execSQL(
                        "INSERT INTO sessions (id, started_at, status, ended_at) "
                                + "VALUES (901, 10, 'COMPLETED', 90)");
                assertNull(db.session(901).runSteps);
                assertNull(db.session(901).checkpointIntervalM);

                RunState state = new RunState();
                state.id = 901;
                state.steps = 22;
                state.runSteps = 0;
                state.walkSteps = 0;
                state.maxRunSpeedKmh = Double.NaN;
                state.checkpointInterval = 0;
                db.snapshot(state, 600, false);
                TrackerDb.Entry row = db.session(901);
                assertNotNull(row);
                assertEquals(10L, row.startedAt);
                assertEquals("COMPLETED", row.status);
                assertEquals(Long.valueOf(90L), row.endedAt);
                assertEquals(22, row.steps);
                assertEquals(Integer.valueOf(0), row.runSteps);
                assertEquals(Integer.valueOf(0), row.walkSteps);
                assertNull(row.maxRunSpeedKmh);
                assertNull(row.checkpointIntervalM);
                assertEquals(1L, count(sql, "sessions"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void snapshotJoinsCallerTransactionAndRollsBackAllFields() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(1);
                SQLiteDatabase sql = db.getWritableDatabase();
                RunState state = new RunState();
                state.id = id;
                state.distanceM = 15.0;
                state.steps = 3;

                sql.beginTransaction();
                try {
                    db.snapshot(state, 1111, true);
                    assertTrue(sql.inTransaction());
                    assertEquals(15.0, db.session(id).distance, 0);
                    assertEquals("COMPLETED", db.session(id).status);
                    // No setTransactionSuccessful(): rollback stays controlled by caller.
                } finally {
                    sql.endTransaction();
                }
                TrackerDb.Entry rolledBack = db.session(id);
                assertEquals(0.0, rolledBack.distance, 0);
                assertEquals(0, rolledBack.steps);
                assertNull(rolledBack.endedAt);
                assertEquals("IN_PROGRESS", rolledBack.status);
                assertFalse(sql.inTransaction());
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void unknownIdDoesNotInsertAndMissingTableStillThrows() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                RunState state = new RunState();
                state.id = 9999L;
                state.distanceM = 50;
                db.snapshot(state, 500, true);
                assertEquals(0L, count(db.getReadableDatabase(), "sessions"));

                db.getWritableDatabase().execSQL("DROP TABLE sessions");
                try {
                    db.snapshot(state, 501, false);
                    fail("legacy SQLiteDatabase.update must propagate a missing-table error");
                } catch (SQLiteException expected) {
                    assertNotNull(expected.getMessage());
                }
            }
        } finally {
            sandbox.cleanUp();
        }
    }
}
