package it.example.passolibero;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.SystemClock;
import android.util.JsonWriter;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Esporta una singola sessione con statistiche e GPS nel documento selezionato
 * dall'utente. Solo Android SDK, nessuna rete e nessun file temporaneo.
 */
public final class SessionJsonExporter {
    public static final int REQUEST_CODE = 741;

    private SessionJsonExporter() { }

    public static void requestSave(Activity activity, long sessionId) {
        if (sessionId <= 0) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "passolibero-sessione-" + sessionId + ".json");
        activity.startActivityForResult(intent, REQUEST_CODE);
    }

    public static void onSaveResult(Activity activity, long sessionId, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null
                || sessionId <= 0) return;
        Uri destination = data.getData();
        Context context = activity.getApplicationContext();
        // Operazione di I/O su thread dedicato, non rallenta la UI né i callback GPS.
        new Thread(() -> {
            try (TrackerDb db = new TrackerDb(context);
                 OutputStream stream = context.getContentResolver().openOutputStream(destination, "w")) {
                if (stream == null) throw new IOException("Destinazione non accessibile");
                try (JsonWriter writer = new JsonWriter(
                        new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
                    writer.setIndent("  ");
                    writeSession(writer, db, context, sessionId);
                    writer.flush();
                }
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Sessione esportata in JSON", Toast.LENGTH_LONG).show());
            } catch (Exception ex) {
                android.util.Log.e("PassoLiberoExport", "Esportazione JSON non riuscita", ex);
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Esportazione JSON non riuscita: " + ex.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }, "SessionJsonExport").start();
    }

    private static void writeSession(JsonWriter writer, TrackerDb db,
                                     Context context, long sessionId) throws IOException {
        TrackerDb.Entry entry = db.session(sessionId);
        if (entry == null) throw new IOException("Sessione non trovata");

        boolean isLive = AppPrefs.get(context).getBoolean("active", false)
                && AppPrefs.get(context).getLong("active_id", 0) == sessionId;
        RunState live = isLive ? RunState.read(AppPrefs.get(context)) : null;
        long exportedAt = System.currentTimeMillis();
        long elapsed = isLive ? Math.max(0, SystemClock.elapsedRealtime() - live.startedElapsedMs)
                : entry.elapsedMs;
        double distance = isLive ? live.distanceM : entry.distance;
        double runM = isLive ? live.runM : entry.runM;
        double walkM = isLive ? live.walkM : entry.walkM;
        long runMs = isLive ? live.runMs : entry.runMs;
        long walkMs = isLive ? live.walkMs : entry.walkMs;
        long movingMs = isLive ? live.movingMs : entry.movingMs;
        long stillMs = isLive ? live.stillMs : entry.stillMs;
        long unknownMs = isLive ? live.unknownMs : entry.unknownMs;
        long movingUnknownMs = isLive ? live.movingUnknownMs : entry.movingUnknownMs;
        int steps = isLive ? live.steps : entry.steps;
        Integer runSteps = isLive ? Integer.valueOf(live.runSteps) : entry.runSteps;
        Integer walkSteps = isLive ? Integer.valueOf(live.walkSteps) : entry.walkSteps;
        int goalM = isLive ? live.goalM : entry.goalM;

        writer.beginObject();
        writer.name("format").value("passolibero-session");
        writer.name("schema_version").value(1);
        writer.name("exported_at_utc_ms").value(exportedAt);
        writer.name("session_id").value(sessionId);
        writer.name("session_status").value(isLive ? "IN_PROGRESS" : entry.status);
        writer.name("started_at_utc_ms").value(entry.startedAt);
        writer.name("ended_at_utc_ms");
        if (isLive || entry.endedAt == null) writer.nullValue();
        else writer.value(entry.endedAt);

        writer.name("totals");
        writer.beginObject();
        writer.name("distance_m").value(distance);
        writer.name("elapsed_ms").value(elapsed);
        writer.name("moving_ms").value(movingMs);
        writer.name("still_ms").value(stillMs);
        writer.name("moving_unknown_ms").value(movingUnknownMs);
        writer.name("unknown_ms").value(unknownMs);
        writer.name("steps").value(steps);
        writer.name("goal_m").value(goalM);
        writePaceAndSpeed(writer, elapsed, distance);
        writer.endObject();

        writer.name("running");
        writeMode(writer, runM, runMs, runSteps);
        writer.name("walking");
        writeMode(writer, walkM, walkMs, walkSteps);

        writer.name("classified_total");
        writer.beginObject();
        double classifiedMeters = SessionTabStats.meters(SessionTabStats.Tab.TOTAL, runM, walkM);
        long classifiedMs = SessionTabStats.milliseconds(SessionTabStats.Tab.TOTAL, runMs, walkMs);
        writer.name("distance_m").value(classifiedMeters);
        writer.name("duration_ms").value(classifiedMs);
        writer.name("steps");
        if (runSteps == null || walkSteps == null) writer.nullValue();
        else writer.value(SessionTabStats.steps(SessionTabStats.Tab.TOTAL, runSteps, walkSteps));
        writePaceAndSpeed(writer, classifiedMs, classifiedMeters);
        writer.endObject();

        writer.name("unclassified");
        writer.beginObject();
        writer.name("distance_m").value(Math.max(0, distance - runM - walkM));
        writer.name("steps");
        if (runSteps == null || walkSteps == null) writer.nullValue();
        else writer.value(Math.max(0, steps - runSteps - walkSteps));
        writer.name("note").value("Non attribuire automaticamente i periodi incerti a corsa o camminata.");
        writer.endObject();

        writer.name("gps_points");
        writer.beginArray();
        writePoints(writer, db, sessionId);
        writer.endArray();
        writer.endObject();
    }

    private static void writeMode(JsonWriter writer, double meters, long milliseconds,
                                  Integer steps) throws IOException {
        writer.beginObject();
        writer.name("distance_m").value(meters);
        writer.name("duration_ms").value(milliseconds);
        writer.name("steps");
        if (steps == null) writer.nullValue();
        else writer.value(steps);
        writer.name("average_cadence_spm");
        double cadence = steps == null ? Double.NaN
                : SessionTabStats.averageCadence(milliseconds, steps);
        if (Double.isFinite(cadence)) writer.value(cadence);
        else writer.nullValue();
        writePaceAndSpeed(writer, milliseconds, meters);
        writer.endObject();
    }

    private static void writePaceAndSpeed(JsonWriter writer, long milliseconds,
                                          double meters) throws IOException {
        writer.name("average_pace_min_per_km");
        String pace = RunMath.pace(milliseconds, meters);
        if ("--:--".equals(pace)) writer.nullValue();
        else writer.value(pace);
        writer.name("average_speed_kmh");
        double speed = SessionTabStats.averageSpeedKmh(milliseconds, meters);
        if (Double.isFinite(speed)) writer.value(speed);
        else writer.nullValue();
    }

    private static void writePoints(JsonWriter writer, TrackerDb db, long sessionId)
            throws IOException {
        SQLiteDatabase sql = db.getReadableDatabase();
        // Cursor SQLite progressivo: nessun ArrayList che cresca con la durata.
        try (Cursor cursor = sql.query("points",
                new String[]{"t_wall", "lat", "lon", "accuracy_m", "speed_kmh",
                        "delta_m", "movement"},
                "session_id=?", new String[]{Long.toString(sessionId)},
                null, null, "id ASC")) {
            while (cursor.moveToNext()) {
                writer.beginObject();
                writer.name("timestamp_utc_ms").value(cursor.getLong(0));
                writer.name("latitude").value(cursor.getDouble(1));
                writer.name("longitude").value(cursor.getDouble(2));
                writer.name("accuracy_m").value(cursor.getDouble(3));
                writer.name("speed_kmh").value(cursor.getDouble(4));
                writer.name("delta_m").value(cursor.getDouble(5));
                writer.name("movement").value(cursor.getString(6));
                writer.endObject();
            }
        }
    }
}
