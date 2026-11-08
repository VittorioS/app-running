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
        super(context, "sessions.db", null, 7);
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
                + "checkpoint_interval_m INTEGER, goal_m INTEGER NOT NULL DEFAULT 0, "
                + "status TEXT NOT NULL DEFAULT 'IN_PROGRESS')");
        db.execSQL("CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id INTEGER NOT NULL, t_wall INTEGER NOT NULL, lat REAL NOT NULL, "
                + "lon REAL NOT NULL, accuracy_m REAL NOT NULL, speed_kmh REAL NOT NULL, "
                + "delta_m REAL NOT NULL, movement TEXT NOT NULL)");
        db.execSQL("CREATE INDEX points_session ON points(session_id)");
        createSprintTable(db);
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
        if (oldV < 7) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN checkpoint_interval_m INTEGER");
            createSprintTable(db);
        }
    }

    private static void createSprintTable(SQLiteDatabase db) {
        // Separata dalla sessione: preserva tutti gli sprint completati o annullati.
        db.execSQL("CREATE TABLE IF NOT EXISTS sprints ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL, "
                + "recorded_at INTEGER NOT NULL, mode TEXT NOT NULL, "
                + "target INTEGER NOT NULL, countdown_s INTEGER NOT NULL, "
                + "elapsed_ms INTEGER NOT NULL, distance_m REAL NOT NULL DEFAULT 0, "
                + "peak_kmh REAL, average_kmh REAL, samples INTEGER NOT NULL DEFAULT 0, "
                + "outcome TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS sprints_session ON sprints(session_id)");
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
        if (s.checkpointInterval > 0) v.put("checkpoint_interval_m", s.checkpointInterval);
        else v.putNull("checkpoint_interval_m");
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

    /** Salva una prova completa solo per la sessione indicata. */
    public void recordSprint(long sessionId, SprintTrial sprint, long elapsedRealtimeMs) {
        if (sessionId <= 0 || sprint == null
                || (sprint.phase() != SprintTrial.Phase.FINISHED
                && sprint.phase() != SprintTrial.Phase.CANCELLED)) return;
        ContentValues v = new ContentValues();
        v.put("session_id", sessionId);
        v.put("recorded_at", System.currentTimeMillis());
        v.put("mode", sprint.mode().name());
        v.put("target", sprint.target());
        v.put("countdown_s", sprint.countdownSeconds());
        v.put("elapsed_ms", sprint.elapsedMs(elapsedRealtimeMs));
        v.put("distance_m", sprint.distanceM());
        if (Double.isFinite(sprint.peakSpeedKmh()) && sprint.peakSpeedKmh() > 0)
            v.put("peak_kmh", sprint.peakSpeedKmh());
        else v.putNull("peak_kmh");
        double average = sprint.averageSpeedKmh(elapsedRealtimeMs);
        if (Double.isFinite(average) && average > 0) v.put("average_kmh", average);
        else v.putNull("average_kmh");
        v.put("samples", sprint.samples());
        v.put("outcome", sprint.phase() == SprintTrial.Phase.CANCELLED
                ? "CANCELLED" : sprint.finishReason());
        getWritableDatabase().insertOrThrow("sprints", null, v);
    }

    public static final class SprintRecord {
        public long id, recordedAt, elapsedMs;
        public int target, countdownSeconds, samples;
        public double distanceM;
        public Double peakKmh, averageKmh;
        public String mode, outcome;
    }

    /** Ordine cronologico, massimo 500 prove per sessione. */
    public List<SprintRecord> sprints(long sessionId) {
        ArrayList<SprintRecord> results = new ArrayList<>();
        if (sessionId <= 0) return results;
        String[] columns = {"id", "recorded_at", "mode", "target", "countdown_s",
                "elapsed_ms", "distance_m", "peak_kmh", "average_kmh", "samples", "outcome"};
        try (Cursor c = getReadableDatabase().query("sprints", columns, "session_id=?",
                new String[]{Long.toString(sessionId)}, null, null,
                "id ASC", "500")) {
            while (c.moveToNext()) {
                SprintRecord sprint = new SprintRecord();
                sprint.id = c.getLong(0);
                sprint.recordedAt = c.getLong(1);
                sprint.mode = c.getString(2);
                sprint.target = c.getInt(3);
                sprint.countdownSeconds = c.getInt(4);
                sprint.elapsedMs = c.getLong(5);
                sprint.distanceM = c.getDouble(6);
                sprint.peakKmh = c.isNull(7) ? null : c.getDouble(7);
                // Le vecchie build salvavano la velocità media 1000x troppo alta.
                // Ricalcolala dai dati originali per correggere anche lo storico.
                double corrected = SprintTrial.averageSpeedKmh(
                        sprint.distanceM, sprint.elapsedMs, c.getInt(9));
                sprint.averageKmh = Double.isFinite(corrected) ? corrected : null;
                sprint.samples = c.getInt(9);
                sprint.outcome = c.getString(10);
                results.add(sprint);
            }
        }
        return results;
    }

    public static final class Entry {
        public long id, startedAt, elapsedMs, movingMs, runMs, walkMs, stillMs, unknownMs, movingUnknownMs;
        public int steps, goalM;
        public Integer runSteps, walkSteps; // NULL for sessions saved before v2.
        public Integer checkpointIntervalM; // NULL for sessions saved before v7.
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
        e.checkpointIntervalM = c.isNull(19) ? null : c.getInt(19);
        return e;
    }

    private static final String[] ENTRY_COLUMNS = {
            "id", "started_at", "elapsed_ms", "distance_m", "steps", "run_m", "walk_m", "status",
            "moving_ms", "walk_ms", "run_ms", "still_ms", "goal_m", "unknown_ms", "moving_unknown_ms",
            "run_steps", "walk_steps", "ended_at", "run_max_speed_kmh",
            "checkpoint_interval_m"
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
            database.delete("sprints", "session_id=?", args);
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
