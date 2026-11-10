package it.example.passolibero.data.local;

import android.content.ContentValues;
import android.database.sqlite.SQLiteOpenHelper;
import it.example.passolibero.application.session.SessionSnapshotWritePort;

/** SQLite update-only adapter for the legacy session snapshot contract. */
public final class SqliteSessionSnapshotAdapter implements SessionSnapshotWritePort {
    private final SQLiteOpenHelper helper;

    public SqliteSessionSnapshotAdapter(SQLiteOpenHelper helper) {
        this.helper = helper;
    }

    @Override
    public void updateSnapshot(Values values, boolean finished) {
        ContentValues row = new ContentValues();
        row.put("distance_m", values.distanceM);
        row.put("elapsed_ms", values.elapsedMs);
        row.put("moving_ms", values.movingMs);
        row.put("steps", values.steps);
        row.put("run_steps", values.runSteps);
        row.put("walk_steps", values.walkSteps);
        if (Double.isFinite(values.maxRunSpeedKmh) && values.maxRunSpeedKmh > 0)
            row.put("run_max_speed_kmh", values.maxRunSpeedKmh);
        else row.putNull("run_max_speed_kmh");
        row.put("walk_ms", values.walkMs);
        row.put("run_ms", values.runMs);
        row.put("still_ms", values.stillMs);
        row.put("unknown_ms", values.unknownMs);
        row.put("moving_unknown_ms", values.movingUnknownMs);
        row.put("walk_m", values.walkM);
        row.put("run_m", values.runM);
        row.put("goal_m", values.goalM);
        if (values.checkpointInterval > 0)
            row.put("checkpoint_interval_m", values.checkpointInterval);
        else row.putNull("checkpoint_interval_m");
        if (finished) {
            row.put("ended_at", System.currentTimeMillis());
            row.put("status", "COMPLETED");
        }
        helper.getWritableDatabase()
                .update("sessions", row, "id=?", new String[] {Long.toString(values.sessionId)});
    }
}
