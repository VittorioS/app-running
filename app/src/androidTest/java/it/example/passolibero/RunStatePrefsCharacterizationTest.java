package it.example.passolibero;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * ADR-002 / #21: observe the original SharedPreferences contract before extraction.
 * Uses a unique preferences file in the test APK sandbox, never the user's data.
 */
@RunWith(AndroidJUnit4.class)
public final class RunStatePrefsCharacterizationTest {
    private static SharedPreferences isolatedPrefs() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return context.getSharedPreferences(
                "issue21-characterization-" + UUID.randomUUID(), Context.MODE_PRIVATE);
    }

    private static long bits(double value) {
        return Double.doubleToLongBits(value);
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        values.put(key, value);
    }

    @Test public void emptyPreferencesRestoreTheOriginalDefaults() {
        SharedPreferences p = isolatedPrefs();
        RunState state = RunState.read(p);
        assertEquals(0, state.id);
        assertEquals(0, state.startedWallMs);
        assertEquals(0, state.startedElapsedMs);
        assertEquals(0, state.walkMs);
        assertEquals(0, state.movingUnknownMs);
        assertEquals(0, state.steps);
        assertEquals(0, state.goalM);
        assertEquals(1000, state.checkpointInterval);
        assertEquals("UNKNOWN", state.movement);
        assertEquals("In attesa del GPS", state.gpsStatus);
        assertFalse(state.lastLocationValid);
        assertEquals(bits(0.0), bits(state.distanceM));
        assertEquals(bits(0.0), bits(state.speedKmh));
        assertEquals(bits(0.0), bits(state.gpsAccuracy));
        assertTrue(Double.isNaN(state.maxRunSpeedKmh));
        assertEquals(bits(Double.NaN), bits(state.maxRunSpeedKmh));
        assertFalse(p.contains("active"));
        assertTrue(p.getAll().isEmpty());
    }

    @Test public void saveUsesExactLegacyKeysTypesBitsAndInactiveMasks() {
        SharedPreferences p = isolatedPrefs();
        RunState s = new RunState();
        s.id = 43;
        s.startedWallMs = 987654321L;
        s.startedElapsedMs = 7777;
        s.lastTickElapsedMs = 333333;
        s.lastStepElapsedMs = 222222;
        s.movingMs = 100;
        s.walkMs = 110;
        s.runMs = 120;
        s.stillMs = 130;
        s.unknownMs = 140;
        s.movingUnknownMs = 150;
        s.steps = 21;
        s.runSteps = 12;
        s.walkSteps = 9;
        s.cadenceSpm = 171;
        s.checkpointIndex = 4;
        s.checkpointInterval = 250;
        s.goalM = 5000;
        s.distanceM = -0.0;
        s.walkM = Double.POSITIVE_INFINITY;
        s.runM = 30.125;
        s.speedKmh = 11.5;
        s.gpsAccuracy = 4.75;
        s.maxRunSpeedKmh = Double.NaN;
        s.lastLatitude = 40.85;
        s.lastLongitude = -14.27;
        s.lastLocationValid = true;
        s.movement = "RUN";
        s.gpsStatus = "GPS pronto";

        long before = System.currentTimeMillis();
        s.save(p, true);
        long after = System.currentTimeMillis();
        Map<String, ?> saved = p.getAll();

        Map<String, Object> expected = new HashMap<>();
        put(expected, "active", true);
        put(expected, "active_id", 43L);
        put(expected, "active_wall", 987654321L);
        put(expected, "active_elapsed", 7777L);
        put(expected, "distance_bits", bits(-0.0));
        put(expected, "walk_distance_bits", bits(Double.POSITIVE_INFINITY));
        put(expected, "run_distance_bits", bits(30.125));
        put(expected, "walk_ms", 110L);
        put(expected, "run_ms", 120L);
        put(expected, "still_ms", 130L);
        put(expected, "unknown_ms", 140L);
        put(expected, "moving_unknown_ms", 150L);
        put(expected, "moving_ms", 100L);
        put(expected, "steps", 21);
        put(expected, "run_steps", 12);
        put(expected, "walk_steps", 9);
        put(expected, "cadence_spm", 171);
        put(expected, "active_goal_m", 5000);
        put(expected, "last_location_valid", true);
        put(expected, "last_latitude_bits", bits(40.85));
        put(expected, "last_longitude_bits", bits(-14.27));
        put(expected, "checkpoint_index", 4);
        put(expected, "checkpoint_interval", 250);
        put(expected, "movement", "RUN");
        put(expected, "gps_status", "GPS pronto");
        put(expected, "speed_bits", bits(11.5));
        put(expected, "run_max_speed_bits", bits(Double.NaN));
        put(expected, "accuracy_bits", bits(4.75));

        assertTrue(saved.containsKey("heartbeat_wall"));
        long heartbeat = p.getLong("heartbeat_wall", -1);
        assertTrue(heartbeat >= before && heartbeat <= after);
        assertEquals(expected.size() + 1, saved.size());
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            assertTrue("missing key " + entry.getKey(), saved.containsKey(entry.getKey()));
            assertEquals("key " + entry.getKey(), entry.getValue(), saved.get(entry.getKey()));
        }
        assertFalse(saved.containsKey("lastTickElapsedMs"));
        assertFalse(saved.containsKey("lastStepElapsedMs"));

        RunState restored = RunState.read(p);
        assertEquals(bits(-0.0), bits(restored.distanceM));
        assertEquals(bits(Double.POSITIVE_INFINITY), bits(restored.walkM));
        assertEquals(bits(Double.NaN), bits(restored.maxRunSpeedKmh));
        assertEquals(s.id, restored.id);
        assertEquals(s.startedWallMs, restored.startedWallMs);
        assertEquals(s.startedElapsedMs, restored.startedElapsedMs);
        assertEquals(bits(s.runM), bits(restored.runM));
        assertEquals(s.walkMs, restored.walkMs);
        assertEquals(s.runMs, restored.runMs);
        assertEquals(s.stillMs, restored.stillMs);
        assertEquals(s.unknownMs, restored.unknownMs);
        assertEquals(s.movingUnknownMs, restored.movingUnknownMs);
        assertEquals(s.movingMs, restored.movingMs);
        assertEquals(s.steps, restored.steps);
        assertEquals(s.runSteps, restored.runSteps);
        assertEquals(s.walkSteps, restored.walkSteps);
        assertEquals(s.cadenceSpm, restored.cadenceSpm);
        assertEquals(s.goalM, restored.goalM);
        assertEquals(s.lastLocationValid, restored.lastLocationValid);
        assertEquals(bits(s.lastLatitude), bits(restored.lastLatitude));
        assertEquals(bits(s.lastLongitude), bits(restored.lastLongitude));
        assertEquals(s.checkpointIndex, restored.checkpointIndex);
        assertEquals(s.checkpointInterval, restored.checkpointInterval);
        assertEquals(s.movement, restored.movement);
        assertEquals(s.gpsStatus, restored.gpsStatus);
        assertEquals(bits(s.speedKmh), bits(restored.speedKmh));
        assertEquals(bits(s.gpsAccuracy), bits(restored.gpsAccuracy));
        assertEquals(s.movingUnknownMs, restored.movingUnknownMs);
        assertEquals(s.checkpointInterval, restored.checkpointInterval);
        assertEquals(s.movement, restored.movement);
        assertEquals(0, restored.lastTickElapsedMs);
        assertEquals(0, restored.lastStepElapsedMs);

        s.save(p, false);
        assertFalse(p.getBoolean("active", true));
        assertEquals(0, p.getLong("active_id", -1));
        assertFalse(p.getBoolean("last_location_valid", true));
        assertEquals(43L, s.id);
        assertTrue(s.lastLocationValid);
        assertEquals(expected.size() + 1, p.getAll().size());
        assertEquals(bits(-0.0), p.getLong("distance_bits", 0));
        assertEquals(bits(40.85), p.getLong("last_latitude_bits", 0));
    }

    @Test public void existingPreferencesAreUpdatedWithoutDroppingUnrelatedKeys() {
        SharedPreferences p = isolatedPrefs();
        p.edit().putString("other_feature", "retained").putLong("active_id", 999).commit();
        RunState s = new RunState();
        s.save(p, true);
        assertEquals("retained", p.getString("other_feature", null));
        assertEquals(0L, p.getLong("active_id", -1));
        assertTrue(p.getBoolean("active", false));
        // Missing preferences default to 1000; a newly saved RunState explicitly writes 0.
        // Characterize that legacy distinction without changing the production contract.
        assertEquals(0, p.getInt("checkpoint_interval", -1));
        assertEquals(0, RunState.read(p).checkpointInterval);
    }

    @Test public void olderPartialPreferencesReadWithoutAnyImplicitMigration() {
        SharedPreferences p = isolatedPrefs();
        p.edit()
                .putLong("active_id", 901L)
                .putLong("distance_bits", bits(-0.0))
                .putLong("run_max_speed_bits", bits(Double.POSITIVE_INFINITY))
                .putInt("checkpoint_interval", 0)
                .putString("movement", "")
                .putString("other_feature", "keep")
                .commit();
        Map<String, ?> before = new HashMap<>(p.getAll());
        RunState restored = RunState.read(p);
        assertEquals(901L, restored.id);
        assertEquals(bits(-0.0), bits(restored.distanceM));
        assertEquals(bits(Double.POSITIVE_INFINITY), bits(restored.maxRunSpeedKmh));
        assertEquals(0, restored.checkpointInterval);
        assertEquals("", restored.movement);
        assertEquals("In attesa del GPS", restored.gpsStatus);
        assertEquals(0, restored.steps);
        assertEquals(before, p.getAll());
    }

    @Test public void wrongStoredTypeStillRaisesClassCastInsteadOfLosingData() {
        SharedPreferences p = isolatedPrefs();
        p.edit().putString("active_id", "invalid").commit();
        try {
            RunState.read(p);
            fail("the legacy typed accessor must reject the wrong on-disk type");
        } catch (ClassCastException expected) {
            assertEquals("invalid", p.getString("active_id", null));
        }
    }

    @Test public void profileUsesTheOriginalKeysAndExactBitEncoding() {
        SharedPreferences p = isolatedPrefs();
        AdaptiveProfile defaults = AppPrefs.loadProfile(p);
        assertEquals(0, defaults.walkSamples);
        assertEquals(0, defaults.runSamples);
        assertEquals(bits(0.0), bits(defaults.walkMean));
        assertEquals(bits(0.0), bits(defaults.runMean));

        AdaptiveProfile profile = new AdaptiveProfile();
        profile.walkSamples = 17;
        profile.runSamples = 19;
        profile.walkMean = -0.0;
        profile.runMean = Double.NaN;
        AppPrefs.saveProfile(p, profile);
        assertEquals(4, p.getAll().size());
        assertEquals(17, p.getInt("model_walk_n", -1));
        assertEquals(19, p.getInt("model_run_n", -1));
        assertEquals(bits(-0.0), p.getLong("model_walk_bits", 0));
        assertEquals(bits(Double.NaN), p.getLong("model_run_bits", 0));
        AdaptiveProfile restored = AppPrefs.loadProfile(p);
        assertEquals(bits(-0.0), bits(restored.walkMean));
        assertEquals(bits(Double.NaN), bits(restored.runMean));
    }
}
