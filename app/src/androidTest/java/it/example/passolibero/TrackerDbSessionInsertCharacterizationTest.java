package it.example.passolibero;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import it.example.passolibero.application.session.SessionCreationPort;
import it.example.passolibero.data.local.SqliteSessionCreationAdapter;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** ADR-002 P3c/#23: characterize the legacy session-insert writer before extraction. */
@RunWith(AndroidJUnit4.class)
public final class TrackerDbSessionInsertCharacterizationTest {
    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue23-create-" + UUID.randomUUID() + "-";

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

    @Test public void newSessionWritesOnlyInitialRowWithOriginalDefaultsAndWall() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                long id = db.newSession(123456789L);
                assertTrue(id > 0);
                assertEquals(7, db.getReadableDatabase().getVersion());
                TrackerDb.Entry created = db.session(id);
                assertNotNull(created);
                assertEquals(id, created.id);
                assertEquals(123456789L, created.startedAt);
                assertEquals("IN_PROGRESS", created.status);
                assertNull(created.endedAt);
                assertNull(created.runSteps);
                assertNull(created.walkSteps);
                assertNull(created.maxRunSpeedKmh);
                assertNull(created.checkpointIntervalM);
                assertEquals(0L, created.elapsedMs);
                assertEquals(0L, created.movingMs);
                assertEquals(0L, created.unknownMs);
                assertEquals(0L, created.movingUnknownMs);
                assertEquals(0, created.goalM);
                assertEquals(0, created.steps);
                assertEquals(0.0, created.distance, 0);

                SQLiteDatabase sql = db.getReadableDatabase();
                assertEquals(1L, count(sql, "sessions"));
                assertEquals(0L, count(sql, "points"));
                assertEquals(0L, count(sql, "sprints"));
                assertEquals(1, db.recent(10).size());
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void newSessionRetainsExistingIdsAndAcceptsUnusualWallValues() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                SQLiteDatabase sql = db.getWritableDatabase();
                // Seed a historical row without changing the table or the user's database.
                sql.execSQL("INSERT INTO sessions (id, started_at) VALUES (?, ?)",
                        new Object[]{700L, 10L});

                long zeroWall = db.newSession(0L);
                long negativeWall = db.newSession(-50L);
                assertTrue(zeroWall > 700L);
                assertTrue(negativeWall > zeroWall);
                assertEquals(0L, db.session(zeroWall).startedAt);
                assertEquals(-50L, db.session(negativeWall).startedAt);
                assertEquals(10L, db.session(700L).startedAt);
                assertEquals("IN_PROGRESS", db.session(700L).status);
                assertEquals(3L, count(sql, "sessions"));

                List<TrackerDb.Entry> sorted = db.recent(3);
                assertEquals(700L, sorted.get(0).id);
                assertEquals(zeroWall, sorted.get(1).id);
                assertEquals(negativeWall, sorted.get(2).id);

                sql.delete("sessions", "id=?", new String[]{"700"});
                long afterDeletion = db.newSession(20L);
                assertTrue(afterDeletion > negativeWall);
                assertNull(db.session(700L));
                assertEquals(3L, count(sql, "sessions"));
                assertEquals(0L, count(sql, "points"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void sessionInsertParticipatesInCallerTransactionWithoutCommittingIt() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                SQLiteDatabase sql = db.getWritableDatabase();
                long rolledBackId;
                sql.beginTransaction();
                try {
                    rolledBackId = db.newSession(4444L);
                    assertNotNull(db.session(rolledBackId));
                    assertTrue(sql.inTransaction());
                    // No setTransactionSuccessful(): the caller rolls back the insert.
                } finally {
                    sql.endTransaction();
                }
                assertNull(db.session(rolledBackId));
                assertEquals(0L, count(sql, "sessions"));

                long committedId = db.newSession(5555L);
                assertNotNull(db.session(committedId));
                assertEquals(1L, count(sql, "sessions"));
                assertFalse(sql.inTransaction());
            }
        } finally {
            sandbox.cleanUp();
        }
    }

    @Test public void malformedStorageErrorIsNotSilentlyTurnedIntoAnId() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                db.getWritableDatabase().execSQL("DROP TABLE sessions");
                try {
                    db.newSession(9L);
                    fail("legacy insertOrThrow must propagate the SQLite error");
                } catch (SQLiteException expected) {
                    assertTrue(expected.getMessage() != null);
                }
            }
        } finally {
            sandbox.cleanUp();
        }
    }
    @Test public void portAndLegacyFacadeMatchOriginalInsertOrThrowExactly() {
        SandboxContext sandbox = sandbox();
        try {
            try (TrackerDb db = new TrackerDb(sandbox)) {
                SQLiteDatabase sql = db.getWritableDatabase();
                // Reproduce the original expression independently of the new adapter.
                ContentValues original = new ContentValues();
                original.put("started_at", 10L);
                long reference = sql.insertOrThrow("sessions", null, original);

                SessionCreationPort port = new SqliteSessionCreationAdapter(db);
                long direct = port.createSession(20L);
                long viaFacade = db.newSession(30L);
                assertTrue(reference > 0);
                assertEquals(reference + 1, direct);
                assertEquals(direct + 1, viaFacade);
                assertEquals(10L, db.session(reference).startedAt);
                assertEquals(20L, db.session(direct).startedAt);
                assertEquals(30L, db.session(viaFacade).startedAt);
                assertEquals("IN_PROGRESS", db.session(direct).status);
                assertNull(db.session(direct).endedAt);
                assertEquals(3L, count(sql, "sessions"));
                assertEquals(0L, count(sql, "points"));
                assertEquals(0L, count(sql, "sprints"));
            }
        } finally {
            sandbox.cleanUp();
        }
    }


}
