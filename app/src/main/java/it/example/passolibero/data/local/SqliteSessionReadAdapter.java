package it.example.passolibero.data.local;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import it.example.passolibero.application.history.SessionReadPort;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only SQLite adapter for schema v7. Owns no database lifetime or write transaction;
 * the legacy TrackerDb helper still opens/closes the database and owns all writers.
 */
public final class SqliteSessionReadAdapter implements SessionReadPort {
    private static final String[] ENTRY_COLUMNS = {
            "id", "started_at", "elapsed_ms", "distance_m", "steps", "run_m", "walk_m", "status",
            "moving_ms", "walk_ms", "run_ms", "still_ms", "goal_m", "unknown_ms", "moving_unknown_ms",
            "run_steps", "walk_steps", "ended_at", "run_max_speed_kmh",
            "checkpoint_interval_m"
    };

    private final SQLiteDatabase database;

    public SqliteSessionReadAdapter(SQLiteDatabase database) {
        this.database = database;
    }

    private static SessionRow readSession(Cursor c) {
                return new SessionRow(
                        c.getLong(0),
                        c.getLong(1),
                        c.getLong(2),
                        c.getDouble(3),
                        c.getInt(4),
                        c.getDouble(5),
                        c.getDouble(6),
                        c.getString(7),
                        c.getLong(8),
                        c.getLong(9),
                        c.getLong(10),
                        c.getLong(11),
                        c.getInt(12),
                        c.getLong(13),
                        c.getLong(14),
                        c.isNull(15) ? null : c.getInt(15),
                        c.isNull(16) ? null : c.getInt(16),
                        c.isNull(17) ? null : c.getLong(17),
                        c.isNull(18) ? null : c.getDouble(18),
                        c.isNull(19) ? null : c.getInt(19));
    }

    @Override
    public List<SessionRow> recent(int count) {
        ArrayList<SessionRow> entries = new ArrayList<>();
        try (Cursor c = database.query(
                "sessions", ENTRY_COLUMNS, null, null, null, null,
                "started_at DESC", Integer.toString(count))) {
            while (c.moveToNext()) {
                entries.add(readSession(c));
            }
        }
        return entries;
    }

    @Override
    public SessionRow session(long sessionId) {
        try (Cursor c = database.query(
                "sessions", ENTRY_COLUMNS, "id=?",
                new String[]{Long.toString(sessionId)}, null, null, null, "1")) {
            return c.moveToFirst() ? readSession(c) : null;
        }
    }

    @Override
    public List<PointRow> pointsAfter(long sessionId, long afterId, int limit) {
        ArrayList<PointRow> points = new ArrayList<>();
        try (Cursor c = database.query(
                "points",
                new String[]{"id", "t_wall", "lat", "lon", "movement",
                        "delta_m", "speed_kmh", "accuracy_m"},
                "session_id=? AND id>?",
                new String[]{Long.toString(sessionId), Long.toString(afterId)},
                null, null, "id ASC", Integer.toString(Math.min(Math.max(limit, 1), 2000)))) {
            while (c.moveToNext()) {
                points.add(new PointRow(
                        c.getLong(0),
                        c.getLong(1),
                        c.getDouble(2),
                        c.getDouble(3),
                        c.getString(4),
                        c.getDouble(5),
                        c.getDouble(6),
                        c.getDouble(7)));
            }
        }
        return points;
    }

    @Override
    public List<SprintRow> sprints(long sessionId) {
        ArrayList<SprintRow> results = new ArrayList<>();
        if (sessionId <= 0) return results;
        String[] columns = {"id", "recorded_at", "mode", "target", "countdown_s",
                "elapsed_ms", "distance_m", "peak_kmh", "average_kmh", "samples", "outcome"};
        try (Cursor c = database.query(
                "sprints", columns, "session_id=?",
                new String[]{Long.toString(sessionId)}, null, null, "id ASC", "500")) {
            while (c.moveToNext()) {
                // The obsolete average_kmh column is intentionally ignored: TrackerDb's
                // legacy facade recalculates the displayed mean from distance/time/samples.
                results.add(new SprintRow(
                        c.getLong(0),
                        c.getLong(1),
                        c.getString(2),
                        c.getInt(3),
                        c.getInt(4),
                        c.getLong(5),
                        c.getDouble(6),
                        c.isNull(7) ? null : c.getDouble(7),
                        c.getInt(9),
                        c.getString(10)));
            }
        }
        return results;
    }

    @Override
    public boolean hasTaggedSprintPoints(long sessionId, long fromWallMs, long untilWallMs) {
        if (sessionId <= 0 || fromWallMs > untilWallMs) return false;
        try (Cursor c = database.rawQuery(
                "SELECT 1 FROM points WHERE session_id=? AND t_wall>=? AND t_wall<=?"
                        + " AND movement LIKE 'SPRINT%' LIMIT 1",
                new String[]{Long.toString(sessionId), Long.toString(fromWallMs),
                        Long.toString(untilWallMs)})) {
            return c.moveToFirst();
        }
    }
}
