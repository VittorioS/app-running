package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.JsonWriter;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** P6c/#30 G1: characterize the legacy JSON GPS stream before moving its SQLite cursor. */
@RunWith(AndroidJUnit4.class)
public final class SessionJsonGpsExportCharacterizationTest {
    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue30-json-" + UUID.randomUUID() + "-";

        SandboxContext(Context base) {
            super(base);
        }

        @Override public Context getApplicationContext() {
            return this;
        }

        @Override public File getDatabasePath(String name) {
            return getBaseContext().getDatabasePath(prefix + name);
        }

        @Override public SQLiteDatabase openOrCreateDatabase(
                String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return getBaseContext().openOrCreateDatabase(prefix + name, mode, factory);
        }

        @Override public SQLiteDatabase openOrCreateDatabase(
                String name, int mode, SQLiteDatabase.CursorFactory factory,
                DatabaseErrorHandler errorHandler) {
            return getBaseContext().openOrCreateDatabase(prefix + name, mode, factory, errorHandler);
        }

        @Override public android.content.SharedPreferences getSharedPreferences(
                String name, int mode) {
            return getBaseContext().getSharedPreferences(prefix + name, mode);
        }

        @Override public boolean deleteDatabase(String name) {
            return getBaseContext().deleteDatabase(prefix + name);
        }

        void cleanUp() {
            getSharedPreferences("passolibero", Context.MODE_PRIVATE).edit().clear().commit();
            deleteDatabase("sessions.db");
        }
    }

    private static void onlyOnDisposableAndroid11Emulator() {
        assumeTrue("API 30 only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Do not run on a personal phone",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    private static void invokeLegacyPoints(JsonWriter writer, TrackerDb db, long sessionId)
            throws Exception {
        // The private method stays as a legacy facade after the adapter extraction.
        Method method = SessionJsonExporter.class.getDeclaredMethod(
                "writePoints", JsonWriter.class, TrackerDb.class, long.class);
        method.setAccessible(true);
        try {
            method.invoke(null, writer, db, sessionId);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof IOException) {
                throw (IOException) error.getCause();
            }
            throw error;
        }
    }

    private static JSONArray exportedPoints(TrackerDb db, long sessionId) throws Exception {
        StringWriter text = new StringWriter();
        JsonWriter writer = new JsonWriter(text);
        writer.beginArray();
        invokeLegacyPoints(writer, db, sessionId);
        writer.endArray();
        writer.close();
        return new JSONArray(text.toString());
    }

    private static JSONObject exportedSession(
            Context context, TrackerDb db, long sessionId) throws Exception {
        Method method = SessionJsonExporter.class.getDeclaredMethod(
                "writeSession", JsonWriter.class, TrackerDb.class, Context.class, long.class);
        method.setAccessible(true);
        StringWriter text = new StringWriter();
        JsonWriter writer = new JsonWriter(text);
        writer.setIndent("  ");
        method.invoke(null, writer, db, context, sessionId);
        writer.close();
        return new JSONObject(text.toString());
    }

    @Test
    public void legacyPointsHaveExactFieldsInInsertOrderNotTimestampOrder() throws Exception {
        onlyOnDisposableAndroid11Emulator();
        SandboxContext context = new SandboxContext(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            try (TrackerDb db = new TrackerDb(context)) {
                long firstSession = db.newSession(100);
                long anotherSession = db.newSession(200);
                db.point(firstSession, 9_000L, 40.25, 14.125, 4.5, 7.25, 2.75, "RUN");
                db.point(anotherSession, 4_000L, 33.0, 12.0, 5.0, 8.0, 3.0, "WALK");
                db.point(firstSession, 1_000L, 40.5, 14.5, 6.5, 3.5, 1.25, "MOVING_UNKNOWN");

                JSONArray points = exportedPoints(db, firstSession);
                assertEquals("Only selected session is exported", 2, points.length());
                JSONObject first = points.getJSONObject(0);
                assertEquals("Do not add/remove legacy GPS fields", 7, first.length());
                assertEquals(9_000L, first.getLong("timestamp_utc_ms"));
                assertEquals(40.25, first.getDouble("latitude"), 0);
                assertEquals(14.125, first.getDouble("longitude"), 0);
                assertEquals(4.5, first.getDouble("accuracy_m"), 0);
                assertEquals(7.25, first.getDouble("speed_kmh"), 0);
                assertEquals(2.75, first.getDouble("delta_m"), 0);
                assertEquals("RUN", first.getString("movement"));

                JSONObject second = points.getJSONObject(1);
                assertEquals(7, second.length());
                assertEquals(1_000L, second.getLong("timestamp_utc_ms"));
                assertEquals(40.5, second.getDouble("latitude"), 0);
                assertEquals(14.5, second.getDouble("longitude"), 0);
                assertEquals(6.5, second.getDouble("accuracy_m"), 0);
                assertEquals(3.5, second.getDouble("speed_kmh"), 0);
                assertEquals(1.25, second.getDouble("delta_m"), 0);
                assertEquals("MOVING_UNKNOWN", second.getString("movement"));
                assertEquals(0, exportedPoints(db, -1).length());
                assertEquals(0, exportedPoints(db, anotherSession + 100).length());

                long beforeUtc = System.currentTimeMillis();
                JSONObject full = exportedSession(context, db, firstSession);
                long afterUtc = System.currentTimeMillis();
                assertEquals("passolibero-session", full.getString("format"));
                assertEquals(1, full.getInt("schema_version"));
                assertEquals(firstSession, full.getLong("session_id"));
                assertEquals("IN_PROGRESS", full.getString("session_status"));
                assertTrue(full.isNull("ended_at_utc_ms"));
                assertTrue(full.getLong("exported_at_utc_ms") >= beforeUtc);
                assertTrue(full.getLong("exported_at_utc_ms") <= afterUtc);
                assertEquals(2, full.getJSONArray("gps_points").length());
                assertEquals(0, full.getJSONArray("sprints").length());
                assertEquals(0, full.getJSONObject("totals").getInt("steps"));
            }
        } finally {
            context.cleanUp();
        }
    }

    @Test
    public void writerIoFailureIsPropagatedWithoutBufferingWholeRoute() throws Exception {
        onlyOnDisposableAndroid11Emulator();
        SandboxContext context = new SandboxContext(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            try (TrackerDb db = new TrackerDb(context)) {
                long id = db.newSession(100);
                db.point(id, 1_000L, 40, 14, 5, 7, 2, "RUN");
                Writer brokenOutput = new Writer() {
                    @Override public void write(char[] value, int offset, int count)
                            throws IOException {
                        throw new IOException("simulated output failure");
                    }
                    @Override public void flush() {}
                    @Override public void close() {}
                };
                try {
                    invokeLegacyPoints(new JsonWriter(brokenOutput), db, id);
                    org.junit.Assert.fail("Writer failure must reach SAF error handler");
                } catch (IOException expected) {
                    assertEquals("simulated output failure", expected.getMessage());
                }
            }
        } finally {
            context.cleanUp();
        }
    }
}
