package it.example.passolibero.data.local;

import android.content.ContentValues;
import android.database.sqlite.SQLiteOpenHelper;
import it.example.passolibero.application.session.SessionSprintWritePort;

/** One SQLite insert for a completed or cancelled sprint, preserving legacy nullable columns. */
public final class SqliteSprintWriteAdapter implements SessionSprintWritePort {
    private final SQLiteOpenHelper helper;

    public SqliteSprintWriteAdapter(SQLiteOpenHelper helper) {
        this.helper = helper;
    }

    @Override
    public void insertSprint(Values values) {
        ContentValues row = new ContentValues();
        row.put("session_id", values.sessionId);
        row.put("recorded_at", values.recordedAtWallMs);
        row.put("mode", values.mode);
        row.put("target", values.target);
        row.put("countdown_s", values.countdownSeconds);
        row.put("elapsed_ms", values.elapsedMs);
        row.put("distance_m", values.distanceM);
        if (Double.isFinite(values.peakSpeedKmh) && values.peakSpeedKmh > 0)
            row.put("peak_kmh", values.peakSpeedKmh);
        else row.putNull("peak_kmh");
        if (Double.isFinite(values.averageSpeedKmh) && values.averageSpeedKmh > 0)
            row.put("average_kmh", values.averageSpeedKmh);
        else row.putNull("average_kmh");
        row.put("samples", values.samples);
        row.put("outcome", values.outcome);
        helper.getWritableDatabase().insertOrThrow("sprints", null, row);
    }
}
