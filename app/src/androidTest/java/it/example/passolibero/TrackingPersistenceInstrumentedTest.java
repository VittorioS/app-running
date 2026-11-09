package it.example.passolibero;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Exercises actual Android persistence without touching the user's sessions.db.
 * Every test redirects the production helper's database and preferences into
 * a uniquely named, temporary namespace within the test app's sandbox.
 */
@RunWith(AndroidJUnit4.class)
public final class TrackingPersistenceInstrumentedTest {
    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue5-" + UUID.randomUUID() + "-";

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

        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler) {
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
            getSharedPreferences("passolibero", Context.MODE_PRIVATE).edit().clear().commit();
            deleteDatabase("sessions.db");
        }
    }

    @Test public void runStateRestoresExactlyAndInactiveSessionLosesActiveMarker() {
        SandboxContext sandbox = new SandboxContext(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            SharedPreferences prefs = AppPrefs.get(sandbox);
            RunState original = new RunState();
            original.id = 37;
            original.startedWallMs = 123456;
            original.startedElapsedMs = 200;
            original.distanceM = 345.125;
            original.runM = 120;
            original.walkM = 225.125;
            original.runMs = 40000;
            original.walkMs = 80000;
            original.movingUnknownMs = 3000;
            original.unknownMs = 2000;
            original.stillMs = 5000;
            original.movingMs = 123000;
            original.steps = 153;
            original.runSteps = 45;
            original.walkSteps = 108;
            original.movement = "RUN";
            original.lastLocationValid = true;
            original.lastLatitude = 40.85;
            original.lastLongitude = 14.27;
            original.maxRunSpeedKmh = 14.75;
            original.save(prefs, true);

            RunState restored = RunState.read(prefs);
            assertTrue(prefs.getBoolean("active", false));
            assertEquals(37, restored.id);
            assertEquals(345.125, restored.distanceM, 0.0000001);
            assertEquals(120, restored.runM, 0.0000001);
            assertEquals(225.125, restored.walkM, 0.0000001);
            assertEquals(123000, restored.movingMs);
            assertEquals(3000, restored.movingUnknownMs);
            assertEquals(153, restored.steps);
            assertEquals("RUN", restored.movement);
            assertTrue(restored.lastLocationValid);
            assertEquals(14.75, restored.maxRunSpeedKmh, 0.0000001);

            restored.save(prefs, false);
            RunState inactive = RunState.read(prefs);
            assertFalse(prefs.getBoolean("active", true));
            assertEquals(0, inactive.id);
            assertFalse(inactive.lastLocationValid);
            assertEquals(345.125, inactive.distanceM, 0.0000001);
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void sqliteSnapshotAndGpsPointsSurviveHelperReopening() {
        SandboxContext sandbox = new SandboxContext(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            long id;
            try (TrackerDb db = new TrackerDb(sandbox)) {
                id = db.newSession(123456);
                RunState saved = new RunState();
                saved.id = id;
                saved.distanceM = 123.75;
                saved.walkM = 82;
                saved.runM = 41.75;
                saved.steps = 60;
                saved.runSteps = 20;
                saved.walkSteps = 40;
                saved.movingMs = 90000;
                saved.runMs = 30000;
                saved.walkMs = 60000;
                saved.goalM = 5000;
                saved.checkpointInterval = 500;
                db.snapshot(saved, 100000, true);
                db.point(id, 123460, 40.851, 14.271, 4, 8, 5, "WALK");
                db.point(id, 123465, 40.852, 14.272, 3, 10, 12, "RUN");
            }
            try (TrackerDb reopened = new TrackerDb(sandbox)) {
                TrackerDb.Entry entry = reopened.session(id);
                assertNotNull(entry);
                assertEquals("COMPLETED", entry.status);
                assertNotNull(entry.endedAt);
                assertEquals(123.75, entry.distance, 0.001);
                assertEquals(5000, entry.goalM);
                assertEquals(Integer.valueOf(500), entry.checkpointIntervalM);
                assertEquals(Integer.valueOf(20), entry.runSteps);
                assertEquals(2, reopened.pointsAfter(id, 0, 10).size());
                assertEquals("RUN", reopened.pointsAfter(id, 0, 10).get(1).movement);
                assertFalse(reopened.recent(10).isEmpty());
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void realSqliteUpgradeFromVersionOnePreservesSessionsAndGpsPoints() {
        SandboxContext sandbox = new SandboxContext(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            // A genuine old database: the Android SQLiteOpenHelper will invoke
            // production onUpgrade(1, 7) when this file is reopened.
            try (SQLiteDatabase legacy = sandbox.openOrCreateDatabase(
                    "sessions.db", Context.MODE_PRIVATE, null)) {
                legacy.execSQL("CREATE TABLE sessions ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, "
                        + "ended_at INTEGER, distance_m REAL NOT NULL DEFAULT 0, "
                        + "elapsed_ms INTEGER NOT NULL DEFAULT 0, "
                        + "moving_ms INTEGER NOT NULL DEFAULT 0, "
                        + "steps INTEGER NOT NULL DEFAULT 0, "
                        + "walk_ms INTEGER NOT NULL DEFAULT 0, "
                        + "run_ms INTEGER NOT NULL DEFAULT 0, "
                        + "still_ms INTEGER NOT NULL DEFAULT 0, "
                        + "walk_m REAL NOT NULL DEFAULT 0, run_m REAL NOT NULL DEFAULT 0, "
                        + "status TEXT NOT NULL DEFAULT 'IN_PROGRESS')");
                legacy.execSQL("CREATE TABLE points ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL, "
                        + "t_wall INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, "
                        + "accuracy_m REAL NOT NULL, speed_kmh REAL NOT NULL, "
                        + "delta_m REAL NOT NULL, movement TEXT NOT NULL)");
                legacy.execSQL("CREATE INDEX points_session ON points(session_id)");
                legacy.execSQL("INSERT INTO sessions (id, started_at, distance_m, steps, status)"
                        + " VALUES (91, 123, 321.5, 56, 'COMPLETED')");
                legacy.execSQL("INSERT INTO points "
                        + "(session_id, t_wall, lat, lon, accuracy_m, speed_kmh, delta_m, movement)"
                        + " VALUES (91, 456, 40.85, 14.27, 4, 9, 3, 'RUN')");
                legacy.setVersion(1);
            }

            try (TrackerDb upgraded = new TrackerDb(sandbox)) {
                assertEquals(7, upgraded.getReadableDatabase().getVersion());
                TrackerDb.Entry old = upgraded.session(91);
                assertNotNull(old);
                assertEquals("COMPLETED", old.status);
                assertEquals(321.5, old.distance, 0.001);
                assertEquals(56, old.steps);
                assertEquals(0, old.goalM);
                assertNull(old.runSteps);
                assertNull(old.walkSteps);
                assertNull(old.checkpointIntervalM);
                List<TrackerDb.TrackPoint> points = upgraded.pointsAfter(91, 0, 10);
                assertEquals(1, points.size());
                assertEquals("RUN", points.get(0).movement);
                try (Cursor cursor = upgraded.getReadableDatabase().rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='sprints'",
                        null)) {
                    assertTrue("Sprint table must exist after migration", cursor.moveToFirst());
                }
                long next = upgraded.newSession(789);
                assertTrue(next > 91);
                assertNotNull(upgraded.session(next));
            }
        } finally {
            sandbox.cleanUp();
        }
    }
}
