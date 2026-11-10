package it.example.passolibero.data.local;

import android.content.SharedPreferences;

import it.example.passolibero.domain.session.AdaptiveProfileSnapshot;
import it.example.passolibero.domain.session.RunSnapshot;

/**
 * SharedPreferences wire-format adapter for issue #21.
 *
 * <p>Keep the legacy keys, types, defaults and operation order. The heartbeat clock read occurs
 * between active_elapsed and distance_bits, and each save ends in a single apply(), not commit().
 */
public final class PreferencesSnapshotMapper {
    private PreferencesSnapshotMapper() {}

    public static RunSnapshot readRun(SharedPreferences p) {
        return new RunSnapshot(
                p.getLong("active_id", 0),
                p.getLong("active_wall", 0),
                p.getLong("active_elapsed", 0),
                Double.longBitsToDouble(p.getLong("distance_bits", Double.doubleToLongBits(0))),
                Double.longBitsToDouble(p.getLong("walk_distance_bits", Double.doubleToLongBits(0))),
                Double.longBitsToDouble(p.getLong("run_distance_bits", Double.doubleToLongBits(0))),
                p.getLong("walk_ms", 0),
                p.getLong("run_ms", 0),
                p.getLong("still_ms", 0),
                p.getLong("unknown_ms", 0),
                p.getLong("moving_unknown_ms", 0),
                p.getLong("moving_ms", 0),
                p.getInt("steps", 0),
                p.getInt("run_steps", 0),
                p.getInt("walk_steps", 0),
                p.getInt("cadence_spm", 0),
                p.getInt("active_goal_m", 0),
                p.getBoolean("last_location_valid", false),
                Double.longBitsToDouble(p.getLong("last_latitude_bits", Double.doubleToLongBits(0))),
                Double.longBitsToDouble(p.getLong("last_longitude_bits", Double.doubleToLongBits(0))),
                p.getInt("checkpoint_index", 0),
                p.getInt("checkpoint_interval", 1000),
                p.getString("movement", "UNKNOWN"),
                p.getString("gps_status", "In attesa del GPS"),
                Double.longBitsToDouble(p.getLong("speed_bits", Double.doubleToLongBits(0))),
                Double.longBitsToDouble(p.getLong("run_max_speed_bits", Double.doubleToLongBits(Double.NaN))),
                Double.longBitsToDouble(p.getLong("accuracy_bits", Double.doubleToLongBits(0))));
    }

    public static void saveRun(SharedPreferences p, RunSnapshot s, boolean active) {
        p.edit()
                .putBoolean("active", active)
                .putLong("active_id", active ? s.id : 0)
                .putLong("active_wall", s.startedWallMs)
                .putLong("active_elapsed", s.startedElapsedMs)
                .putLong("heartbeat_wall", System.currentTimeMillis())
                .putLong("distance_bits", Double.doubleToLongBits(s.distanceM))
                .putLong("walk_distance_bits", Double.doubleToLongBits(s.walkM))
                .putLong("run_distance_bits", Double.doubleToLongBits(s.runM))
                .putLong("walk_ms", s.walkMs)
                .putLong("run_ms", s.runMs)
                .putLong("still_ms", s.stillMs)
                .putLong("unknown_ms", s.unknownMs)
                .putLong("moving_unknown_ms", s.movingUnknownMs)
                .putLong("moving_ms", s.movingMs)
                .putInt("steps", s.steps)
                .putInt("run_steps", s.runSteps)
                .putInt("walk_steps", s.walkSteps)
                .putInt("cadence_spm", s.cadenceSpm)
                .putInt("active_goal_m", s.goalM)
                .putBoolean("last_location_valid", active && s.lastLocationValid)
                .putLong("last_latitude_bits", Double.doubleToLongBits(s.lastLatitude))
                .putLong("last_longitude_bits", Double.doubleToLongBits(s.lastLongitude))
                .putInt("checkpoint_index", s.checkpointIndex)
                .putInt("checkpoint_interval", s.checkpointInterval)
                .putString("movement", s.movement)
                .putString("gps_status", s.gpsStatus)
                .putLong("speed_bits", Double.doubleToLongBits(s.speedKmh))
                .putLong("run_max_speed_bits", Double.doubleToLongBits(s.maxRunSpeedKmh))
                .putLong("accuracy_bits", Double.doubleToLongBits(s.gpsAccuracy)).apply();
    }

    public static AdaptiveProfileSnapshot readProfile(SharedPreferences p) {
        return new AdaptiveProfileSnapshot(
                p.getInt("model_walk_n", 0),
                p.getInt("model_run_n", 0),
                Double.longBitsToDouble(p.getLong("model_walk_bits", Double.doubleToLongBits(0))),
                Double.longBitsToDouble(p.getLong("model_run_bits", Double.doubleToLongBits(0))));
    }

    public static void saveProfile(SharedPreferences p, AdaptiveProfileSnapshot s) {
        p.edit()
                .putInt("model_walk_n", s.walkSamples)
                .putInt("model_run_n", s.runSamples)
                .putLong("model_walk_bits", Double.doubleToLongBits(s.walkMean))
                .putLong("model_run_bits", Double.doubleToLongBits(s.runMean))
                .apply();
    }
}
