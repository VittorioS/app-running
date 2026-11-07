package it.example.passolibero;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Storico offline. Migrazione conservativa: mantiene tutte le sessioni preesistenti. */
public final class TrackerDb extends SQLiteOpenHelper {
    public TrackerDb(Context context) {
        super(context, "sessions.db", null, 6);
        appContext = context.getApplicationContext();
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, ended_at INTEGER, "
                + "distance_m REAL NOT NULL DEFAULT 0, elapsed_ms INTEGER NOT NULL DEFAULT 0, "
                + "moving_ms INTEGER NOT NULL DEFAULT 0, steps INTEGER NOT NULL DEFAULT 0, "
                + "walk_ms INTEGER NOT NULL DEFAULT 0, run_ms INTEGER NOT NULL DEFAULT 0, still_ms INTEGER NOT NULL DEFAULT 0, unknown_ms INTEGER NOT NULL DEFAULT 0, "
                + "moving_unknown_ms INTEGER NOT NULL DEFAULT 0, "
                + "run_steps INTEGER, walk_steps INTEGER, "
                + "run_max_speed_kmh REAL, "
                + "walk_m REAL NOT NULL DEFAULT 0, run_m REAL NOT NULL DEFAULT 0, "
                + "goal_m INTEGER NOT NULL DEFAULT 0, "
                + "status TEXT NOT NULL DEFAULT 'IN_PROGRESS')");
        db.execSQL("CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id INTEGER NOT NULL, t_wall INTEGER NOT NULL, lat REAL NOT NULL, "
                + "lon REAL NOT NULL, accuracy_m REAL NOT NULL, speed_kmh REAL NOT NULL, "
                + "delta_m REAL NOT NULL, movement TEXT NOT NULL)");
        db.execSQL("CREATE INDEX points_session ON points(session_id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) db.execSQL("ALTER TABLE sessions ADD COLUMN goal_m INTEGER NOT NULL DEFAULT 0");
        if (oldV < 3) db.execSQL("ALTER TABLE sessions ADD COLUMN unknown_ms INTEGER NOT NULL DEFAULT 0");
        if (oldV < 4) db.execSQL("ALTER TABLE sessions ADD COLUMN moving_unknown_ms INTEGER NOT NULL DEFAULT 0");
        // NULL preserves the distinction between unavailable historical data and zero steps.
        if (oldV < 5) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN run_steps INTEGER");
            db.execSQL("ALTER TABLE sessions ADD COLUMN walk_steps INTEGER");
        }
        if (oldV < 6) db.execSQL("ALTER TABLE sessions ADD COLUMN run_max_speed_kmh REAL");
    }

    public long newSession(long wall) {
        ContentValues v = new ContentValues();
        v.put("started_at", wall);
        return getWritableDatabase().insertOrThrow("sessions", null, v);
    }

    public void snapshot(RunState s, long elapsedMs, boolean finished) {
        ContentValues v = new ContentValues();
        v.put("distance_m", s.distanceM);
        v.put("elapsed_ms", elapsedMs);
        v.put("moving_ms", s.movingMs);
        v.put("steps", s.steps);
        v.put("run_steps", s.runSteps);
        v.put("walk_steps", s.walkSteps);
        if (Double.isFinite(s.maxRunSpeedKmh) && s.maxRunSpeedKmh > 0)
            v.put("run_max_speed_kmh", s.maxRunSpeedKmh);
        else v.putNull("run_max_speed_kmh");
        v.put("walk_ms", s.walkMs);
        v.put("run_ms", s.runMs);
        v.put("still_ms", s.stillMs);
        v.put("unknown_ms", s.unknownMs);
        v.put("moving_unknown_ms", s.movingUnknownMs);
        v.put("walk_m", s.walkM);
        v.put("run_m", s.runM);
        v.put("goal_m", s.goalM);
        if (finished) {
            v.put("ended_at", System.currentTimeMillis());
            v.put("status", "COMPLETED");
        }
        getWritableDatabase().update("sessions", v, "id=?", new String[]{Long.toString(s.id)});
    }

    public void point(long id, long wall, double lat, double lon, double acc,
                      double speed, double delta, MovementClassifier.Kind kind) {
        ContentValues v = new ContentValues();
        v.put("session_id", id);
        v.put("t_wall", wall);
        v.put("lat", lat);
        v.put("lon", lon);
        v.put("accuracy_m", acc);
        v.put("speed_kmh", speed);
        v.put("delta_m", delta);
        v.put("movement", kind.name());
        getWritableDatabase().insertOrThrow("points", null, v);
    }

    public static final class Entry {
        public long id, startedAt, elapsedMs, movingMs, runMs, walkMs, stillMs, unknownMs, movingUnknownMs;
        public int steps, goalM;
        public Integer runSteps, walkSteps; // NULL for sessions saved before v2.
        public Double maxRunSpeedKmh; // NULL for old sessions or no reliable run measurement.
        public Long endedAt; // NULL for sessions still in progress.
        public double distance, runM, walkM;
        public String status;
    }

    private static Entry readEntry(Cursor c) {
        Entry e = new Entry();
        e.id = c.getLong(0);
        e.startedAt = c.getLong(1);
        e.elapsedMs = c.getLong(2);
        e.distance = c.getDouble(3);
        e.steps = c.getInt(4);
        e.runM = c.getDouble(5);
        e.walkM = c.getDouble(6);
        e.status = c.getString(7);
        e.movingMs = c.getLong(8);
        e.walkMs = c.getLong(9);
        e.runMs = c.getLong(10);
        e.stillMs = c.getLong(11);
        e.goalM = c.getInt(12);
        e.unknownMs = c.getLong(13);
        e.movingUnknownMs = c.getLong(14);
        e.runSteps = c.isNull(15) ? null : c.getInt(15);
        e.walkSteps = c.isNull(16) ? null : c.getInt(16);
        e.endedAt = c.isNull(17) ? null : c.getLong(17);
        e.maxRunSpeedKmh = c.isNull(18) ? null : c.getDouble(18);
        return e;
    }

    private static final String[] ENTRY_COLUMNS = {
            "id", "started_at", "elapsed_ms", "distance_m", "steps", "run_m", "walk_m", "status",
            "moving_ms", "walk_ms", "run_ms", "still_ms", "goal_m", "unknown_ms", "moving_unknown_ms",
            "run_steps", "walk_steps", "ended_at", "run_max_speed_kmh"
    };

    public List<Entry> recent(int count) {
        ArrayList<Entry> entries = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("sessions", ENTRY_COLUMNS,
                null, null, null, null, "started_at DESC", Integer.toString(count))) {
            while (c.moveToNext()) entries.add(readEntry(c));
        }
        return entries;
    }

    public Entry session(long id) {
        try (Cursor c = getReadableDatabase().query("sessions", ENTRY_COLUMNS,
                "id=?", new String[]{Long.toString(id)}, null, null, null, "1")) {
            return c.moveToFirst() ? readEntry(c) : null;
        }
    }

    /**
     * Elimina solo la sessione indicata e i suoi punti GPS, in una transazione.
     * Non permette mai di cancellare la sessione che il tracking sta registrando.
     * Una sessione interrotta (non più attiva) può invece essere eliminata.
     */
    public boolean deleteSession(long sessionId) {
        if (sessionId <= 0) return false;
        android.content.SharedPreferences prefs = AppPrefs.get(getContextForHistory());
        if (isCurrentSession(prefs, sessionId)) return false;

        SQLiteDatabase database = getWritableDatabase();
        String[] args = {Long.toString(sessionId)};
        database.beginTransaction();
        try {
            if (isCurrentSession(prefs, sessionId)) return false;
            int removed = database.delete("sessions", "id=?", args);
            if (removed != 1) return false;
            database.delete("points", "session_id=?", args);
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    private final Context appContext;

    private Context getContextForHistory() {
        return appContext;
    }

    private static boolean isCurrentSession(android.content.SharedPreferences p, long id) {
        return p.getBoolean("active", false) && p.getLong("active_id", 0) == id;
    }

    public static final class TrackPoint {
        public long id, time;
        public double lat, lon;
        public String movement;
    }

    /** Recupero incrementale, per non ricaricare il percorso intero a ogni refresh della Home. */
    public List<TrackPoint> pointsAfter(long sessionId, long afterId, int limit) {
        ArrayList<TrackPoint> points = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("points",
                new String[]{"id", "t_wall", "lat", "lon", "movement"},
                "session_id=? AND id>?", new String[]{Long.toString(sessionId), Long.toString(afterId)},
                null, null, "id ASC", Integer.toString(Math.min(Math.max(limit, 1), 2000)))) {
            while (c.moveToNext()) {
                TrackPoint p = new TrackPoint();
                p.id = c.getLong(0);
                p.time = c.getLong(1);
                p.lat = c.getDouble(2);
                p.lon = c.getDouble(3);
                p.movement = c.getString(4);
                points.add(p);
            }
        }
        return points;
    }
}
