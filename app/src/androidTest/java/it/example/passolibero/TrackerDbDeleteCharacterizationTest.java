package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * ADR-002/#28: characterize real SQLite history deletion in an isolated database.
 *
 * <p>Every test uses uniquely named preferences and SQLite storage, never the user's history.
 */
@RunWith(AndroidJUnit4.class)
public final class TrackerDbDeleteCharacterizationTest {
    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue28-delete-" + UUID.randomUUID() + "-";

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

        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return getBaseContext().getSharedPreferences(isolated(name), mode);
        }

        @Override public boolean deleteDatabase(String name) {
            return getBaseContext().deleteDatabase(isolated(name));
        }

        void cleanUp() {
            getSharedPreferences("passolibero", Context.MODE_PRIVATE).edit().clear().commit();
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

    private static void addChildRows(TrackerDb db, long sessionId) {
        db.point(sessionId, 100, 40.0, 14.0, 3.0, 5.0, 2.0, "RUN");
        ContentValues sprint = new ContentValues();
        sprint.put("session_id", sessionId);
        sprint.put("recorded_at", 200L);
        sprint.put("mode", "TIME");
        sprint.put("target", 30);
        sprint.put("countdown_s", 3);
        sprint.put("elapsed_ms", 10000L);
        sprint.put("distance_m", 20.0);
        sprint.put("samples", 2);
        sprint.put("outcome", "COMPLETED");
        db.getWritableDatabase().insertOrThrow("sprints", null, sprint);
    }

    @Test public void deletingOneSessionRemovesOnlyItsRowsAndBothChildTables() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long deletedId = db.newSession(100);
                long preservedId = db.newSession(200);
                addChildRows(db, deletedId);
                addChildRows(db, preservedId);

                assertTrue(HistoryDataBridge.deleteSession(sandbox, deletedId));
                assertNull(db.session(deletedId));
                assertNotNull(db.session(preservedId));
                SQLiteDatabase sql = db.getReadableDatabase();
                assertEquals(1L, count(sql, "sessions"));
                assertEquals(1L, count(sql, "points"));
                assertEquals(1L, count(sql, "sprints"));
                assertEquals(1, db.pointsAfter(preservedId, 0, 10).size());
                assertEquals(1, db.sprints(preservedId).size());
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void activeSessionCannotBeDeletedButCanAfterActiveFlagCleared() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long activeId = db.newSession(100);
                addChildRows(db, activeId);
                SharedPreferences prefs = AppPrefs.get(sandbox);
                prefs.edit().putBoolean("active", true)
                        .putLong("active_id", activeId).commit();

                assertFalse(HistoryDataBridge.deleteSession(sandbox, activeId));
                assertNotNull(db.session(activeId));
                assertEquals(1L, count(db.getReadableDatabase(), "sessions"));
                assertEquals(1L, count(db.getReadableDatabase(), "points"));
                assertEquals(1L, count(db.getReadableDatabase(), "sprints"));

                prefs.edit().putBoolean("active", false).commit();
                assertTrue(HistoryDataBridge.deleteSession(sandbox, activeId));
                assertNull(db.session(activeId));
                assertEquals(0L, count(db.getReadableDatabase(), "points"));
                assertEquals(0L, count(db.getReadableDatabase(), "sprints"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void invalidAndMissingSessionIdsDoNotCreateOrDeleteRows() {
        SandboxContext sandbox = sandbox();
        try {
            assertFalse(sandbox.getDatabasePath("sessions.db").exists());
            assertFalse(HistoryDataBridge.deleteSession(sandbox, 0));
            assertFalse(HistoryDataBridge.deleteSession(sandbox, -4));
            assertFalse(sandbox.getDatabasePath("sessions.db").exists());

            try (TrackerDb db = new TrackerDb(sandbox)) {
                long existingId = db.newSession(100);
                addChildRows(db, existingId);
                assertFalse(HistoryDataBridge.deleteSession(sandbox, existingId + 100));
                assertTrue(HistoryDataBridge.deleteSession(sandbox, existingId));
                assertFalse(HistoryDataBridge.deleteSession(sandbox, existingId));
                assertEquals(0L, count(db.getReadableDatabase(), "sessions"));
                assertEquals(0L, count(db.getReadableDatabase(), "points"));
                assertEquals(0L, count(db.getReadableDatabase(), "sprints"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void failureDeletingPointsRollsBackSessionAndSprintRemoval() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(100);
                addChildRows(db, id);
                SQLiteDatabase sql = db.getWritableDatabase();
                sql.execSQL("CREATE TRIGGER reject_delete BEFORE DELETE ON points "
                        + "BEGIN SELECT RAISE(ABORT, 'injected delete failure'); END");
                try {
                    HistoryDataBridge.deleteSession(sandbox, id);
                    fail("A child-row write failure must propagate");
                } catch (SQLiteException expected) {
                    // The existing session delete preceded the points delete inside
                    // the transaction, and must be rolled back with the child rows.
                }

                assertNotNull("Rollback preserves the session", db.session(id));
                assertEquals(1L, count(sql, "sessions"));
                assertEquals(1L, count(sql, "points"));
                assertEquals(1L, count(sql, "sprints"));
                assertFalse(sql.inTransaction());

                sql.execSQL("DROP TRIGGER reject_delete");
                assertTrue(HistoryDataBridge.deleteSession(sandbox, id));
                assertEquals(0L, count(sql, "sessions"));
                assertEquals(0L, count(sql, "points"));
                assertEquals(0L, count(sql, "sprints"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }
}
