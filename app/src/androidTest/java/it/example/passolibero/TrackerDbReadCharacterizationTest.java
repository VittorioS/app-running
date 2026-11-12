package it.example.passolibero;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import it.example.passolibero.application.history.SessionReadPort;
import it.example.passolibero.data.local.SqliteSessionReadAdapter;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** ADR-002 P3b: characterize the unchanged SQLite read contract before extracting a port. */
@RunWith(AndroidJUnit4.class)
public final class TrackerDbReadCharacterizationTest {
    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue22-read-" + UUID.randomUUID() + "-";

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

    @Test public void recentIsNewestByStartedAtAndSessionPreservesEveryColumn() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long older = db.newSession(100);
                long newest = db.newSession(300);
                long middle = db.newSession(200);
                RunState state = new RunState();
                state.id = newest;
                state.distanceM = 123.75;
                state.walkM = 23.25;
                state.runM = 100.5;
                state.steps = 42;
                state.runSteps = 13;
                state.walkSteps = 29;
                state.movingMs = 9876;
                state.walkMs = 111;
                state.runMs = 222;
                state.stillMs = 333;
                state.unknownMs = 444;
                state.movingUnknownMs = 555;
                state.goalM = 6500;
                state.checkpointInterval = 500;
                state.maxRunSpeedKmh = 14.25;
                db.snapshot(state, 12345, true);

                List<TrackerDb.Entry> recent = db.recent(2);
                assertEquals(2, recent.size());
                assertEquals(newest, recent.get(0).id);
                assertEquals(middle, recent.get(1).id);
                assertTrue(db.recent(0).isEmpty());

                TrackerDb.Entry entry = db.session(newest);
                assertNotNull(entry);
                assertEquals(300L, entry.startedAt);
                assertNotNull(entry.endedAt);
                assertEquals("COMPLETED", entry.status);
                assertEquals(12345L, entry.elapsedMs);
                assertEquals(9876L, entry.movingMs);
                assertEquals(111L, entry.walkMs);
                assertEquals(222L, entry.runMs);
                assertEquals(333L, entry.stillMs);
                assertEquals(444L, entry.unknownMs);
                assertEquals(555L, entry.movingUnknownMs);
                assertEquals(42, entry.steps);
                assertEquals(6500, entry.goalM);
                assertEquals(Integer.valueOf(13), entry.runSteps);
                assertEquals(Integer.valueOf(29), entry.walkSteps);
                assertEquals(Integer.valueOf(500), entry.checkpointIntervalM);
                assertEquals(Double.valueOf(14.25), entry.maxRunSpeedKmh);
                assertEquals(123.75, entry.distance, 0);
                assertEquals(23.25, entry.walkM, 0);
                assertEquals(100.5, entry.runM, 0);
                assertEquals(older, db.recent(3).get(2).id);
                assertNull(db.session(0));
                assertNull(db.session(-10));
                assertNull(db.session(999999));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void unsnapshottedSessionRetainsLegacyNullsAndDefaults() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(10);
                TrackerDb.Entry entry = db.session(id);
                assertNotNull(entry);
                assertEquals("IN_PROGRESS", entry.status);
                assertNull(entry.endedAt);
                assertNull(entry.runSteps);
                assertNull(entry.walkSteps);
                assertNull(entry.maxRunSpeedKmh);
                assertNull(entry.checkpointIntervalM);
                assertEquals(0, entry.goalM);
                assertEquals(0L, entry.unknownMs);
                assertEquals(0L, entry.movingUnknownMs);
                assertEquals(0.0, entry.distance, 0);
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void pointsArePagedByIdAndLimitsAreClamped() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long session = db.newSession(1);
                long other = db.newSession(2);
                db.point(session, 300, 40.1, 14.1, 3.5, 8.5, 1.5, "WALK");
                db.point(other, 310, 41.0, 15.0, 2, 3, 0, "RUN");
                db.point(session, 100, 40.2, 14.2, 4.5, 9.5, 2.5, "SPRINT:42");
                db.point(session, 200, 40.3, 14.3, 5.5, 10.5, 3.5, "RUN");

                List<TrackerDb.TrackPoint> first = db.pointsAfter(session, 0, 2);
                assertEquals(2, first.size());
                assertTrue(first.get(0).id < first.get(1).id);
                assertEquals(300, first.get(0).time);
                assertEquals("WALK", first.get(0).movement);
                assertEquals(40.1, first.get(0).lat, 0);
                assertEquals(14.1, first.get(0).lon, 0);
                assertEquals(1.5, first.get(0).deltaM, 0);
                assertEquals(8.5, first.get(0).speedKmh, 0);
                assertEquals(3.5, first.get(0).accuracyM, 0);
                assertEquals(100, first.get(1).time);
                assertEquals("SPRINT:42", first.get(1).movement);

                List<TrackerDb.TrackPoint> next = db.pointsAfter(session, first.get(1).id, 2);
                assertEquals(1, next.size());
                assertEquals(200, next.get(0).time);
                assertEquals("RUN", next.get(0).movement);
                assertEquals(first.get(0).id, db.pointsAfter(session, 0, -5).get(0).id);
                assertEquals(1, db.pointsAfter(session, 0, 0).size());
                assertEquals(3, db.pointsAfter(session, 0, Integer.MAX_VALUE).size());
                assertTrue(db.pointsAfter(other, next.get(0).id, 10).isEmpty());
                assertTrue(db.pointsAfter(99999, 0, 10).isEmpty());
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    private static void sprintRow(
            TrackerDb db, long sessionId, long recordedAt, String mode, int samples,
            double meters, long elapsedMs, Double peakKmh, double obsoleteAverage) {
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("recorded_at", recordedAt);
        values.put("mode", mode);
        values.put("target", 20);
        values.put("countdown_s", 5);
        values.put("elapsed_ms", elapsedMs);
        values.put("distance_m", meters);
        if (peakKmh == null) values.putNull("peak_kmh");
        else values.put("peak_kmh", peakKmh);
        values.put("average_kmh", obsoleteAverage);
        values.put("samples", samples);
        values.put("outcome", "COMPLETED");
        db.getWritableDatabase().insertOrThrow("sprints", null, values);
    }

    @Test public void sprintsUseIdOrderingAndRecomputeLegacyAverage() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long a = db.newSession(1);
                long b = db.newSession(2);
                sprintRow(db, a, 500, "DISTANCE", 3, 100, 20000, 21.5, 18000);
                sprintRow(db, b, 200, "TIME", 2, 100, 20000, 9.0, 18000);
                sprintRow(db, a, 100, "TIME", 1, 0, 20000, null, 18000);

                List<TrackerDb.SprintRecord> rows = db.sprints(a);
                assertEquals(2, rows.size());
                assertTrue(rows.get(0).id < rows.get(1).id);
                assertEquals(500, rows.get(0).recordedAt);
                assertEquals("DISTANCE", rows.get(0).mode);
                assertEquals(20, rows.get(0).target);
                assertEquals(5, rows.get(0).countdownSeconds);
                assertEquals(20000L, rows.get(0).elapsedMs);
                assertEquals(100.0, rows.get(0).distanceM, 0);
                assertEquals(Double.valueOf(21.5), rows.get(0).peakKmh);
                assertEquals(Double.valueOf(18.0), rows.get(0).averageKmh);
                assertEquals(3, rows.get(0).samples);
                assertEquals("COMPLETED", rows.get(0).outcome);
                assertNull(rows.get(1).peakKmh);
                assertNull(rows.get(1).averageKmh);
                assertEquals("TIME", rows.get(1).mode);
                assertTrue(db.sprints(0).isEmpty());
                assertTrue(db.sprints(-1).isEmpty());
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void taggedSprintPointWindowIsInclusiveAndSessionScoped() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long a = db.newSession(1);
                long b = db.newSession(2);
                db.point(a, 200, 40, 14, 4, 0, 0, "WALK");
                db.point(b, 300, 40, 14, 4, 0, 0, "SPRINT:other");
                db.point(a, 300, 40, 14, 4, 0, 0, "SPRINT:mine");
                assertFalse(db.hasTaggedSprintPoints(a, 200, 299));
                assertTrue(db.hasTaggedSprintPoints(a, 300, 300));
                assertTrue(db.hasTaggedSprintPoints(a, 200, 300));
                assertFalse(db.hasTaggedSprintPoints(a, 301, 500));
                assertFalse(db.hasTaggedSprintPoints(a, 500, 300));
                assertFalse(db.hasTaggedSprintPoints(0, 0, 500));
                assertFalse(db.hasTaggedSprintPoints(-1, 0, 500));
                assertTrue(db.hasTaggedSprintPoints(b, 300, 300));
            }
        } finally {
            sandbox.cleanUp();
        }
    }
    @Test public void newReadPortMatchesLegacyFacadeOnRealIsolatedSQLite() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(100);
                long other = db.newSession(200);
                RunState state = new RunState();
                state.id = id;
                state.distanceM = 10.75;
                state.runMs = 4500;
                state.runSteps = 2;
                state.checkpointInterval = 250;
                db.snapshot(state, 6000, false);
                db.point(id, 301, 40.0, 14.0, 3.0, 5.0, 1.0, "RUN");
                db.point(id, 302, 40.1, 14.1, 4.0, 6.0, 2.0, "SPRINT:1");
                sprintRow(db, id, 302, "DISTANCE", 2, 50, 10000, null, 99999);

                SessionReadPort reader = new SqliteSessionReadAdapter(db.getReadableDatabase());
                SessionReadPort.SessionRow portSession = reader.session(id);
                TrackerDb.Entry oldSession = db.session(id);
                assertNotNull(portSession);
                assertEquals(oldSession.id, portSession.id);
                assertEquals(oldSession.distance, portSession.distance, 0);
                assertEquals(oldSession.runMs, portSession.runMs);
                assertEquals(oldSession.runSteps, portSession.runSteps);
                assertEquals(oldSession.checkpointIntervalM, portSession.checkpointIntervalM);
                assertEquals(oldSession.maxRunSpeedKmh, portSession.maxRunSpeedKmh);
                assertEquals(other, reader.recent(1).get(0).id);
                assertEquals(db.recent(2).get(1).id, reader.recent(2).get(1).id);
                assertNull(reader.session(-1));

                List<SessionReadPort.PointRow> points = reader.pointsAfter(id, 0, 1);
                assertEquals(1, points.size());
                assertEquals(db.pointsAfter(id, 0, 1).get(0).id, points.get(0).id);
                assertEquals(40.0, points.get(0).lat, 0);
                assertEquals(db.pointsAfter(id, points.get(0).id, 1).get(0).id,
                        reader.pointsAfter(id, points.get(0).id, 1).get(0).id);
                assertEquals(db.hasTaggedSprintPoints(id, 300, 303),
                        reader.hasTaggedSprintPoints(id, 300, 303));

                List<SessionReadPort.SprintRow> sprints = reader.sprints(id);
                assertEquals(1, sprints.size());
                assertEquals(db.sprints(id).get(0).id, sprints.get(0).id);
                assertEquals(db.sprints(id).get(0).distanceM, sprints.get(0).distanceM, 0);
                assertEquals(2, sprints.get(0).samples);
                assertEquals(Double.valueOf(18.0), db.sprints(id).get(0).averageKmh);
                assertTrue(reader.sprints(0).isEmpty());
            }
        } finally {
            sandbox.cleanUp();
        }
    }




    @Test public void historyListUseCaseMatchesLegacySqliteOrderAndNullColumns() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long oldest = db.newSession(100);
                long newest = db.newSession(300);
                long middle = db.newSession(200);
                RunState saved = new RunState();
                saved.id = newest;
                saved.distanceM = 123.75;
                saved.steps = 23;
                saved.checkpointInterval = 500;
                db.snapshot(saved, 12_345L, true);

                List<TrackerDb.Entry> legacy = db.recent(200);
                List<SessionReadPort.SessionRow> migrated = HistoryDataBridge.listRecent(sandbox);
                assertEquals(legacy.size(), migrated.size());
                assertEquals(3, migrated.size());
                for (int i = 0; i < legacy.size(); i++) {
                    TrackerDb.Entry before = legacy.get(i);
                    SessionReadPort.SessionRow after = migrated.get(i);
                    assertEquals(before.id, after.id);
                    assertEquals(before.startedAt, after.startedAt);
                    assertEquals(before.elapsedMs, after.elapsedMs);
                    assertEquals(before.distance, after.distance, 0);
                    assertEquals(before.steps, after.steps);
                    assertEquals(before.status, after.status);
                    assertEquals(before.endedAt, after.endedAt);
                    assertEquals(before.checkpointIntervalM, after.checkpointIntervalM);
                }
                assertEquals(newest, migrated.get(0).id);
                assertEquals(middle, migrated.get(1).id);
                assertEquals(oldest, migrated.get(2).id);
                assertNull("Legacy null checkpoint column must remain null",
                        migrated.get(1).checkpointIntervalM);
                assertNotNull("Completed row has an end timestamp",
                        migrated.get(0).endedAt);

                // List refresh must be read-only; no session is inserted, edited or deleted.
                assertEquals(3, db.recent(200).size());
                assertEquals("IN_PROGRESS", db.session(oldest).status);
            }
        } finally {
            sandbox.cleanUp();
        }
    }
}
