package it.example.passolibero;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
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

/** ADR-002 P3c/#23: characterize the legacy sprint insert before extracting its writer. */
@RunWith(AndroidJUnit4.class)
public final class TrackerDbSprintInsertCharacterizationTest {
    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue23-sprint-" + UUID.randomUUID() + "-";

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

    private static long count(SQLiteDatabase database, String table) {
        return DatabaseUtils.longForQuery(
                database, "SELECT COUNT(*) FROM " + table, null);
    }

    private static SprintTrial finishedTimeSprintWithSamples() {
        SprintTrial sprint = new SprintTrial();
        sprint.start(1000L, 5, SprintTrial.Mode.TIME, 5);
        sprint.advance(6000L);
        assertTrue(sprint.onFix(6500L, 6500L, 3.0, 0.5, 4.0));
        assertTrue(sprint.onFix(7500L, 7500L, 5.0, 0.5, 4.0));
        sprint.advance(11000L);
        assertEquals(SprintTrial.Phase.FINISHED, sprint.phase());
        return sprint;
    }

    private static SprintTrial cancelledDistanceSprint() {
        SprintTrial sprint = new SprintTrial();
        sprint.start(1000L, 5, SprintTrial.Mode.DISTANCE, 50);
        sprint.advance(6000L);
        sprint.cancel(8000L);
        assertEquals(SprintTrial.Phase.CANCELLED, sprint.phase());
        return sprint;
    }

    @Test public void finishedSprintWritesOriginalRawColumnsAndWallTime() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long sessionId = db.newSession(100);
                SprintTrial sprint = finishedTimeSprintWithSamples();
                assertEquals(2, sprint.samples());
                assertEquals(4.0, sprint.distanceM(), 0);
                long before = System.currentTimeMillis();
                db.recordSprint(sessionId, sprint, 12000L);
                long after = System.currentTimeMillis();

                SQLiteDatabase sql = db.getReadableDatabase();
                assertEquals(7, sql.getVersion());
                assertEquals(1L, count(sql, "sessions"));
                assertEquals(0L, count(sql, "points"));
                assertEquals(1L, count(sql, "sprints"));
                try (Cursor c = sql.rawQuery(
                        "SELECT id, session_id, recorded_at, mode, target, countdown_s, "
                                + "elapsed_ms, distance_m, peak_kmh, average_kmh, samples, "
                                + "outcome FROM sprints", null)) {
                    assertTrue(c.moveToFirst());
                    assertTrue(c.getLong(0) > 0);
                    assertEquals(sessionId, c.getLong(1));
                    assertTrue(c.getLong(2) >= before && c.getLong(2) <= after);
                    assertEquals("TIME", c.getString(3));
                    assertEquals(5, c.getInt(4));
                    assertEquals(5, c.getInt(5));
                    assertEquals(5000L, c.getLong(6));
                    assertEquals(4.0, c.getDouble(7), 0);
                    assertEquals(18.0, c.getDouble(8), 0);
                    assertEquals(sprint.averageSpeedKmh(12000L), c.getDouble(9), 0.000001);
                    assertEquals(2, c.getInt(10));
                    assertEquals("TEMPO", c.getString(11));
                    assertFalse(c.moveToNext());
                }
                assertEquals(1, db.sprints(sessionId).size());
                assertEquals("TEMPO", db.sprints(sessionId).get(0).outcome);
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void cancelledSprintRetainsOriginalModeAndNullableMeasurements() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(100);
                SprintTrial sprint = cancelledDistanceSprint();
                db.recordSprint(id, sprint, 9000L);
                try (Cursor c = db.getReadableDatabase().rawQuery(
                        "SELECT mode, target, countdown_s, elapsed_ms, distance_m, "
                                + "peak_kmh, average_kmh, samples, outcome "
                                + "FROM sprints WHERE session_id=?",
                        new String[] {Long.toString(id)})) {
                    assertTrue(c.moveToFirst());
                    assertEquals("DISTANCE", c.getString(0));
                    assertEquals(50, c.getInt(1));
                    assertEquals(5, c.getInt(2));
                    assertEquals(2000L, c.getLong(3));
                    assertEquals(0.0, c.getDouble(4), 0);
                    assertTrue(c.isNull(5));
                    assertTrue(c.isNull(6));
                    assertEquals(0, c.getInt(7));
                    assertEquals("CANCELLED", c.getString(8));
                    assertFalse(c.moveToNext());
                }
                assertEquals(1L, count(db.getReadableDatabase(), "sprints"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void finishedWithZeroSpeedsWritesNullPeakAndAverage() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(1);
                SprintTrial sprint = new SprintTrial();
                sprint.start(1000L, 5, SprintTrial.Mode.TIME, 5);
                sprint.advance(6000L);
                assertTrue(sprint.onFix(6500L, 6500L, 0, 0.5, 3));
                assertTrue(sprint.onFix(7500L, 7500L, 0, 0.5, 3));
                sprint.advance(11000L);
                assertEquals(SprintTrial.Phase.FINISHED, sprint.phase());
                assertEquals(2, sprint.samples());

                db.recordSprint(id, sprint, 12000L);
                try (Cursor c = db.getReadableDatabase().rawQuery(
                        "SELECT peak_kmh, average_kmh, samples, distance_m "
                                + "FROM sprints WHERE session_id=?",
                        new String[] {Long.toString(id)})) {
                    assertTrue(c.moveToFirst());
                    assertTrue(c.isNull(0));
                    assertTrue(c.isNull(1));
                    assertEquals(2, c.getInt(2));
                    assertEquals(0.0, c.getDouble(3), 0);
                }
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void invalidSessionAndNonterminalPhasesProduceNoRows() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(5);
                SprintTrial sprint = new SprintTrial();
                db.recordSprint(id, null, 1000L);
                db.recordSprint(id, sprint, 1000L); // IDLE
                sprint.start(1000L, 5, SprintTrial.Mode.TIME, 5);
                db.recordSprint(id, sprint, 2000L); // COUNTDOWN
                sprint.advance(6000L);
                db.recordSprint(id, sprint, 7000L); // ACTIVE
                sprint.advance(11000L);
                db.recordSprint(0, sprint, 12000L);
                db.recordSprint(-1, sprint, 12000L);
                assertEquals(0L, count(db.getReadableDatabase(), "sprints"));
                db.recordSprint(id, sprint, 12000L);
                assertEquals(1L, count(db.getReadableDatabase(), "sprints"));
                assertEquals(1L, count(db.getReadableDatabase(), "sessions"));
                assertEquals(0L, count(db.getReadableDatabase(), "points"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void duplicateCallsAppendRowsWithoutDeduplication() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(10);
                SprintTrial sprint = finishedTimeSprintWithSamples();
                db.recordSprint(id, sprint, 12000L);
                db.recordSprint(id, sprint, 13000L);
                assertEquals(2L, count(db.getReadableDatabase(), "sprints"));
                try (Cursor c = db.getReadableDatabase().rawQuery(
                        "SELECT id, elapsed_ms, outcome FROM sprints ORDER BY id ASC", null)) {
                    assertTrue(c.moveToFirst());
                    long first = c.getLong(0);
                    assertEquals(5000L, c.getLong(1));
                    assertEquals("TEMPO", c.getString(2));
                    assertTrue(c.moveToNext());
                    assertTrue(c.getLong(0) > first);
                    assertEquals(5000L, c.getLong(1));
                    assertEquals("TEMPO", c.getString(2));
                    assertFalse(c.moveToNext());
                }
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void sprintInsertJoinsCallerTransactionWithoutCommitting() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(1);
                SQLiteDatabase sql = db.getWritableDatabase();
                SprintTrial sprint = cancelledDistanceSprint();
                sql.beginTransaction();
                try {
                    db.recordSprint(id, sprint, 9000L);
                    assertEquals(1L, count(sql, "sprints"));
                    assertTrue(sql.inTransaction());
                    // Caller does not mark the transaction successful.
                } finally {
                    sql.endTransaction();
                }
                assertEquals(0L, count(sql, "sprints"));
                db.recordSprint(id, sprint, 9000L);
                assertEquals(1L, count(sql, "sprints"));
                assertFalse(sql.inTransaction());
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void unknownSessionDoesNotInsertParentAndMissingTableThrows() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                SprintTrial sprint = cancelledDistanceSprint();
                db.recordSprint(98765L, sprint, 9000L);
                assertEquals(0L, count(db.getReadableDatabase(), "sessions"));
                assertEquals(1L, count(db.getReadableDatabase(), "sprints"));
                db.getWritableDatabase().execSQL("DROP TABLE sprints");
                try {
                    db.recordSprint(98765L, sprint, 9000L);
                    fail("legacy insertOrThrow must propagate SQLite errors");
                } catch (SQLiteException expected) {
                    assertNotNull(expected.getMessage());
                }
            }
        } finally {
            sandbox.cleanUp();
        }
    }
}
