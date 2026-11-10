package it.example.passolibero;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import it.example.passolibero.application.history.SessionReadPort;
import it.example.passolibero.data.local.SqliteSessionReadAdapter;
import it.example.passolibero.data.local.SqliteSessionCreationAdapter;
import it.example.passolibero.data.local.SqlitePointWriteAdapter;

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

    /** Legacy facade; the single insert is now owned by data/local. */
    public long newSession(long wall) {
        return new SqliteSessionCreationAdapter(this).createSession(wall);
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
        point(id, wall, lat, lon, acc, speed, delta, kind.name());
    }

    /** Per i tratti Sprint, la tipologia e' un'etichetta della mappa,
     * indipendente dal classificatore RUN/WALK. Nessuna migrazione necessaria. */
    public void point(long id, long wall, double lat, double lon, double acc,
                      double speed, double delta, String movement) {
        new SqlitePointWriteAdapter(this).writePoint(
                id, wall, lat, lon, acc, speed, delta, movement);
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


    /** Ordine cronologico per id, massimo 500 prove per sessione. */
    public List<SprintRecord> sprints(long sessionId) {
        ArrayList<SprintRecord> results = new ArrayList<>();
        if (sessionId <= 0) return results;
        for (SessionReadPort.SprintRow row : sessionReader().sprints(sessionId)) {
            SprintRecord sprint = legacySprintRecord(row);
            // Historical builds saved an average 1000x too high: retain the correction.
            double corrected = SprintTrial.averageSpeedKmh(
                    sprint.distanceM, sprint.elapsedMs, sprint.samples);
            sprint.averageKmh = Double.isFinite(corrected) ? corrected : null;
            results.add(sprint);
        }
        return results;
    }

    private static SprintRecord legacySprintRecord(SessionReadPort.SprintRow row) {
        SprintRecord result = new SprintRecord();
        result.id = row.id;
        result.recordedAt = row.recordedAt;
        result.mode = row.mode;
        result.target = row.target;
        result.countdownSeconds = row.countdownSeconds;
        result.elapsedMs = row.elapsedMs;
        result.distanceM = row.distanceM;
        result.peakKmh = row.peakKmh;
        result.samples = row.samples;
        result.outcome = row.outcome;
        return result;
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

    /** Keeps existing mutable Entry API while SQL lives behind the read port. */
    private SessionReadPort sessionReader() {
        return new SqliteSessionReadAdapter(getReadableDatabase());
    }

    private static Entry legacyEntry(SessionReadPort.SessionRow row) {
        Entry result = new Entry();
        result.id = row.id;
        result.startedAt = row.startedAt;
        result.elapsedMs = row.elapsedMs;
        result.distance = row.distance;
        result.steps = row.steps;
        result.runM = row.runM;
        result.walkM = row.walkM;
        result.status = row.status;
        result.movingMs = row.movingMs;
        result.walkMs = row.walkMs;
        result.runMs = row.runMs;
        result.stillMs = row.stillMs;
        result.goalM = row.goalM;
        result.unknownMs = row.unknownMs;
        result.movingUnknownMs = row.movingUnknownMs;
        result.runSteps = row.runSteps;
        result.walkSteps = row.walkSteps;
        result.endedAt = row.endedAt;
        result.maxRunSpeedKmh = row.maxRunSpeedKmh;
        result.checkpointIntervalM = row.checkpointIntervalM;
        return result;
    }


    public List<Entry> recent(int count) {
        ArrayList<Entry> entries = new ArrayList<>();
        for (SessionReadPort.SessionRow row : sessionReader().recent(count)) {
            entries.add(legacyEntry(row));
        }
        return entries;
    }

    public Entry session(long id) {
        SessionReadPort.SessionRow row = sessionReader().session(id);
        return row == null ? null : legacyEntry(row);
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

    /**
     * Un vecchio sprint si ricostruisce dagli orari solo se non possiede gia'
     * punti GNSS con l'etichetta esplicita SPRINT o SPRINT:<id>.
     */
    public boolean hasTaggedSprintPoints(long sessionId, long fromWallMs, long untilWallMs) {
        if (sessionId <= 0 || fromWallMs > untilWallMs) return false;
        return sessionReader().hasTaggedSprintPoints(sessionId, fromWallMs, untilWallMs);
    }

    public static final class TrackPoint {
        public long id, time;
        public double lat, lon, deltaM, speedKmh, accuracyM;
        public String movement;
    }

    /** Incremental points page, preserving ascending row ID and 1–2000 SQL limit. */
    public List<TrackPoint> pointsAfter(long sessionId, long afterId, int limit) {
        ArrayList<TrackPoint> points = new ArrayList<>();
        for (SessionReadPort.PointRow row : sessionReader().pointsAfter(sessionId, afterId, limit)) {
            points.add(legacyTrackPoint(row));
        }
        return points;
    }

    private static TrackPoint legacyTrackPoint(SessionReadPort.PointRow row) {
        TrackPoint result = new TrackPoint();
        result.id = row.id;
        result.time = row.time;
        result.lat = row.lat;
        result.lon = row.lon;
        result.movement = row.movement;
        result.deltaM = row.deltaM;
        result.speedKmh = row.speedKmh;
        result.accuracyM = row.accuracyM;
        return result;
    }

}
