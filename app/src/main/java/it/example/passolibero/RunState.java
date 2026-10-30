package it.example.passolibero;

import android.content.SharedPreferences;

/** Stato minimo persistito una volta al secondo e dopo ogni update significativo. */
public final class RunState {
    public long id, startedWallMs, startedElapsedMs, lastTickElapsedMs;
    public long movingMs, walkMs, runMs, stillMs;
    public int steps, cadenceSpm, checkpointIndex, checkpointInterval, goalM;
    public double distanceM, walkM, runM, speedKmh, gpsAccuracy;
    public double lastLatitude, lastLongitude;
    public boolean lastLocationValid;
    public String movement = "UNKNOWN", gpsStatus = "In attesa del GPS";
    public long lastStepElapsedMs;

    public static RunState read(SharedPreferences p) {
        RunState s = new RunState();
        s.id = p.getLong("active_id", 0);
        s.startedWallMs = p.getLong("active_wall", 0);
        s.startedElapsedMs = p.getLong("active_elapsed", 0);
        s.distanceM = Double.longBitsToDouble(p.getLong("distance_bits", Double.doubleToLongBits(0)));
        s.walkM = Double.longBitsToDouble(p.getLong("walk_distance_bits", Double.doubleToLongBits(0)));
        s.runM = Double.longBitsToDouble(p.getLong("run_distance_bits", Double.doubleToLongBits(0)));
        s.walkMs = p.getLong("walk_ms", 0);
        s.runMs = p.getLong("run_ms", 0);
        s.stillMs = p.getLong("still_ms", 0);
        s.movingMs = p.getLong("moving_ms", 0);
        s.steps = p.getInt("steps", 0);
        s.cadenceSpm = p.getInt("cadence_spm", 0);
        s.goalM = p.getInt("active_goal_m", 0);
        s.lastLocationValid = p.getBoolean("last_location_valid", false);
        s.lastLatitude = Double.longBitsToDouble(p.getLong("last_latitude_bits", Double.doubleToLongBits(0)));
        s.lastLongitude = Double.longBitsToDouble(p.getLong("last_longitude_bits", Double.doubleToLongBits(0)));
        s.checkpointIndex = p.getInt("checkpoint_index", 0);
        s.checkpointInterval = p.getInt("checkpoint_interval", 1000);
        s.movement = p.getString("movement", "UNKNOWN");
        s.gpsStatus = p.getString("gps_status", "In attesa del GPS");
        s.speedKmh = Double.longBitsToDouble(p.getLong("speed_bits", Double.doubleToLongBits(0)));
        s.gpsAccuracy = Double.longBitsToDouble(p.getLong("accuracy_bits", Double.doubleToLongBits(0)));
        return s;
    }

    public void save(SharedPreferences p, boolean active) {
        p.edit().putBoolean("active", active).putLong("active_id", active ? id : 0)
                .putLong("active_wall", startedWallMs).putLong("active_elapsed", startedElapsedMs)
                .putLong("heartbeat_wall", System.currentTimeMillis())
                .putLong("distance_bits", Double.doubleToLongBits(distanceM))
                .putLong("walk_distance_bits", Double.doubleToLongBits(walkM))
                .putLong("run_distance_bits", Double.doubleToLongBits(runM))
                .putLong("walk_ms", walkMs).putLong("run_ms", runMs)
                .putLong("still_ms", stillMs).putLong("moving_ms", movingMs)
                .putInt("steps", steps).putInt("cadence_spm", cadenceSpm)
                .putInt("active_goal_m", goalM)
                .putBoolean("last_location_valid", active && lastLocationValid)
                .putLong("last_latitude_bits", Double.doubleToLongBits(lastLatitude))
                .putLong("last_longitude_bits", Double.doubleToLongBits(lastLongitude))
                .putInt("checkpoint_index", checkpointIndex)
                .putInt("checkpoint_interval", checkpointInterval)
                .putString("movement", movement).putString("gps_status", gpsStatus)
                .putLong("speed_bits", Double.doubleToLongBits(speedKmh))
                .putLong("accuracy_bits", Double.doubleToLongBits(gpsAccuracy)).apply();
    }
}
