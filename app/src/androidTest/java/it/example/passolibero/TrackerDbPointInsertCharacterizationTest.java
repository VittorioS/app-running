package it.example.passolibero;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import it.example.passolibero.application.session.PointWritePort;
import it.example.passolibero.data.local.SqlitePointWriteAdapter;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** ADR-002 P3c/#23: characterize the legacy GPS-point insert before extracting its writer. */
@RunWith(AndroidJUnit4.class)
public final class TrackerDbPointInsertCharacterizationTest {
    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue23-point-" + UUID.randomUUID() + "-";

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

    @Test public void legacyPointWritesAllEightColumnsWithoutUnrelatedRows() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long sessionId = db.newSession(10);
                db.point(sessionId, 456789L, 40.8518, 14.2681,
                        3.75, 8.25, 12.5, "SPRINT:37");

                SQLiteDatabase sql = db.getReadableDatabase();
                assertEquals(7, sql.getVersion());
                assertEquals(1L, count(sql, "sessions"));
                assertEquals(1L, count(sql, "points"));
                assertEquals(0L, count(sql, "sprints"));
                try (Cursor cursor = sql.rawQuery(
                        "SELECT id, session_id, t_wall, lat, lon, accuracy_m, speed_kmh, "
                                + "delta_m, movement FROM points", null)) {
                    assertTrue(cursor.moveToFirst());
                    assertTrue(cursor.getLong(0) > 0);
                    assertEquals(sessionId, cursor.getLong(1));
                    assertEquals(456789L, cursor.getLong(2));
                    assertEquals(40.8518, cursor.getDouble(3), 0);
                    assertEquals(14.2681, cursor.getDouble(4), 0);
                    assertEquals(3.75, cursor.getDouble(5), 0);
                    assertEquals(8.25, cursor.getDouble(6), 0);
                    assertEquals(12.5, cursor.getDouble(7), 0);
                    assertEquals("SPRINT:37", cursor.getString(8));
                    assertFalse(cursor.moveToNext());
                }

                List<TrackerDb.TrackPoint> points = db.pointsAfter(sessionId, 0, 10);
                assertEquals(1, points.size());
                assertEquals("SPRINT:37", points.get(0).movement);
                assertEquals(12.5, points.get(0).deltaM, 0);
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void bothOverloadsPreserveLabelsAndIdsWithEqualWallTime() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long firstSession = db.newSession(100);
                long secondSession = db.newSession(101);
                db.point(firstSession, 333L, 40.1, 14.1, 2, 5, 1,
                        MovementClassifier.Kind.RUN);
                db.point(secondSession, 333L, 41.2, 15.2, 4, 8, 2, "WALK");
                db.point(firstSession, 333L, 40.3, 14.3, 3, 9, 3, "SPRINT:4");

                List<TrackerDb.TrackPoint> first = db.pointsAfter(firstSession, 0, 10);
                List<TrackerDb.TrackPoint> second = db.pointsAfter(secondSession, 0, 10);
                assertEquals(2, first.size());
                assertEquals(1, second.size());
                assertTrue(first.get(0).id < second.get(0).id);
                assertTrue(second.get(0).id < first.get(1).id);
                assertEquals("RUN", first.get(0).movement);
                assertEquals("WALK", second.get(0).movement);
                assertEquals("SPRINT:4", first.get(1).movement);
                assertEquals(333L, first.get(0).time);
                assertEquals(333L, first.get(1).time);
                assertEquals(1, db.pointsAfter(firstSession, first.get(0).id, 1).size());
                assertEquals(first.get(1).id,
                        db.pointsAfter(firstSession, first.get(0).id, 1).get(0).id);
                assertTrue(db.pointsAfter(firstSession, first.get(1).id, 10).isEmpty());
                assertEquals(3L, count(db.getReadableDatabase(), "points"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void pointInsertParticipatesInCallerTransactionAndCanRollback() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long sessionId = db.newSession(1);
                SQLiteDatabase sql = db.getWritableDatabase();
                sql.beginTransaction();
                try {
                    db.point(sessionId, 77, 40, 14, 2, 3, 1, "RUN");
                    assertTrue(sql.inTransaction());
                    assertEquals(1L, count(sql, "points"));
                    // No setTransactionSuccessful(): caller rolls back this write.
                } finally {
                    sql.endTransaction();
                }
                assertEquals(0L, count(sql, "points"));
                assertEquals(1L, count(sql, "sessions"));
                db.point(sessionId, 78, 41, 15, 3, 4, 2, "WALK");
                assertEquals(1L, count(sql, "points"));
                assertFalse(sql.inTransaction());
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void unknownSessionIdStillWritesWithoutAddingASession() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                db.point(98765L, 123L, 40.0, 14.0, 4, 5, 6, "UNKNOWN");
                assertEquals(0L, count(db.getReadableDatabase(), "sessions"));
                assertEquals(1L, count(db.getReadableDatabase(), "points"));
                assertEquals(1, db.pointsAfter(98765L, 0, 10).size());
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void invalidMovementAndMissingTablePropagateSQLiteError() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long sessionId = db.newSession(1);
                try {
                    db.point(sessionId, 111, 40, 14, 1, 1, 0, (String) null);
                    fail("movement is NOT NULL and original insertOrThrow must fail");
                } catch (SQLiteException expected) {
                    assertNotNull(expected.getMessage());
                }
                assertEquals(0L, count(db.getReadableDatabase(), "points"));
                db.getWritableDatabase().execSQL("DROP TABLE points");
                try {
                    db.point(sessionId, 112, 40, 14, 1, 1, 0, "RUN");
                    fail("missing points table must propagate SQLite exception");
                } catch (SQLiteException expected) {
                    assertNotNull(expected.getMessage());
                }
            }
        } finally {
            sandbox.cleanUp();
        }
    }
    @Test public void portAndLegacyPointWritesMatchOriginalSqlRowForRow() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long sessionId = db.newSession(1);
                SQLiteDatabase sql = db.getWritableDatabase();

                // Reference: execute the exact original ContentValues + insertOrThrow.
                ContentValues original = new ContentValues();
                original.put("session_id", sessionId);
                original.put("t_wall", 234L);
                original.put("lat", 40.25);
                original.put("lon", 14.5);
                original.put("accuracy_m", 3.25);
                original.put("speed_kmh", 8.75);
                original.put("delta_m", 1.125);
                original.put("movement", "SPRINT:9");
                long firstId = sql.insertOrThrow("points", null, original);

                PointWritePort port = new SqlitePointWriteAdapter(db);
                port.writePoint(
                        sessionId, 234L, 40.25, 14.5, 3.25, 8.75, 1.125, "SPRINT:9");
                db.point(sessionId, 234L, 40.25, 14.5, 3.25, 8.75, 1.125, "SPRINT:9");

                assertTrue(firstId > 0);
                try (Cursor cursor = sql.rawQuery(
                        "SELECT id, session_id, t_wall, lat, lon, accuracy_m, speed_kmh, "
                                + "delta_m, movement FROM points ORDER BY id ASC", null)) {
                    long previous = 0;
                    for (int i = 0; i < 3; i++) {
                        assertTrue(cursor.moveToNext());
                        long current = cursor.getLong(0);
                        assertTrue(current > previous);
                        if (i == 0) assertEquals(firstId, current);
                        previous = current;
                        assertEquals(sessionId, cursor.getLong(1));
                        assertEquals(234L, cursor.getLong(2));
                        assertEquals(40.25, cursor.getDouble(3), 0);
                        assertEquals(14.5, cursor.getDouble(4), 0);
                        assertEquals(3.25, cursor.getDouble(5), 0);
                        assertEquals(8.75, cursor.getDouble(6), 0);
                        assertEquals(1.125, cursor.getDouble(7), 0);
                        assertEquals("SPRINT:9", cursor.getString(8));
                    }
                    assertFalse(cursor.moveToNext());
                }
                assertEquals(3L, count(sql, "points"));
                assertEquals(1L, count(sql, "sessions"));
                assertEquals(0L, count(sql, "sprints"));
                assertEquals(3, db.pointsAfter(sessionId, 0, 10).size());
            }
        } finally {
            sandbox.cleanUp();
        }
    }


}
