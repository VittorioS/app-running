package it.example.passolibero;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** SQLite nativo, append-only per punti; sessioni interrotte restano consultabili. */
public final class TrackerDb extends SQLiteOpenHelper {
    public TrackerDb(Context context) { super(context, "sessions.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, ended_at INTEGER, "
                + "distance_m REAL NOT NULL DEFAULT 0, elapsed_ms INTEGER NOT NULL DEFAULT 0, "
                + "moving_ms INTEGER NOT NULL DEFAULT 0, steps INTEGER NOT NULL DEFAULT 0, "
                + "walk_ms INTEGER NOT NULL DEFAULT 0, run_ms INTEGER NOT NULL DEFAULT 0, still_ms INTEGER NOT NULL DEFAULT 0, "
                + "walk_m REAL NOT NULL DEFAULT 0, run_m REAL NOT NULL DEFAULT 0, "
                + "status TEXT NOT NULL DEFAULT 'IN_PROGRESS')");
        db.execSQL("CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id INTEGER NOT NULL, t_wall INTEGER NOT NULL, lat REAL NOT NULL, "
                + "lon REAL NOT NULL, accuracy_m REAL NOT NULL, speed_kmh REAL NOT NULL, "
                + "delta_m REAL NOT NULL, movement TEXT NOT NULL)");
        db.execSQL("CREATE INDEX points_session ON points(session_id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // Gli aggiornamenti futuri dovranno migrare lo schema, senza cancellare lo storico.
        throw new IllegalStateException("Migrazione database non definita: " + oldV + " -> " + newV);
    }

    public long newSession(long wall) {
        ContentValues v = new ContentValues();
        v.put("started_at", wall);
        return getWritableDatabase().insertOrThrow("sessions", null, v);
    }

    public void snapshot(RunState s, long elapsedMs, boolean finished) {
        ContentValues v = new ContentValues();
        v.put("distance_m", s.distanceM); v.put("elapsed_ms", elapsedMs);
        v.put("moving_ms", s.movingMs); v.put("steps", s.steps);
        v.put("walk_ms", s.walkMs); v.put("run_ms", s.runMs); v.put("still_ms", s.stillMs);
        v.put("walk_m", s.walkM); v.put("run_m", s.runM);
        if (finished) {
            v.put("ended_at", System.currentTimeMillis());
            v.put("status", "COMPLETED");
        }
        getWritableDatabase().update("sessions", v, "id=?", new String[]{Long.toString(s.id)});
    }

    public void point(long id, long wall, double lat, double lon, double acc,
                      double speed, double delta, MovementClassifier.Kind kind) {
        ContentValues v = new ContentValues();
        v.put("session_id", id); v.put("t_wall", wall);
        v.put("lat", lat); v.put("lon", lon); v.put("accuracy_m", acc);
        v.put("speed_kmh", speed); v.put("delta_m", delta); v.put("movement", kind.name());
        getWritableDatabase().insertOrThrow("points", null, v);
    }

    public static final class Entry {
        public long id, startedAt, elapsedMs;
        public int steps;
        public double distance, runM, walkM;
        public String status;
    }

    public List<Entry> recent(int count) {
        ArrayList<Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("sessions",
                new String[]{"id", "started_at", "elapsed_ms", "distance_m", "steps", "run_m", "walk_m", "status"},
                null, null, null, null, "started_at DESC", Integer.toString(count))) {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.id = c.getLong(0); e.startedAt = c.getLong(1);
                e.elapsedMs = c.getLong(2); e.distance = c.getDouble(3);
                e.steps = c.getInt(4); e.runM = c.getDouble(5);
                e.walkM = c.getDouble(6); e.status = c.getString(7);
                result.add(e);
            }
        }
        return result;
    }
}
