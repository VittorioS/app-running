package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DatabaseUtils;
import android.os.Build;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * P4a/#24 G1: characterize STOP when the Service has not initialized yet but a
 * previously persisted session is marked active. No GPS, START or FGS is used.
 *
 * <p>Disposable Android 11 emulator only. A synthetic row and preferences are
 * restored/removed after the test; this must never run on a real handset.
 */
@RunWith(AndroidJUnit4.class)
public final class TrackingServiceEarlyActiveStopInstrumentedTest {
    @SuppressWarnings("deprecation")
    private static boolean serviceRunning(Context context) {
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo running : manager.getRunningServices(50)) {
            if (TrackingService.class.getName().equals(running.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void restorePreferences(
            SharedPreferences prefs, Map<String, ?> original) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : original.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Set) {
                editor.putStringSet(key, new HashSet<>((Set<String>) value));
            } else {
                throw new AssertionError("Unexpected preference type for " + key);
            }
        }
        assertTrue("Restore the original preference snapshot", editor.commit());
    }

    private static long sessionRows(TrackerDb db) {
        return DatabaseUtils.longForQuery(
                db.getReadableDatabase(), "SELECT COUNT(*) FROM sessions", null);
    }

    @Test
    public void stopBeforeInitializeHydratesAndFinishesPersistedSessionWithoutNewSession() {
        assumeTrue("API 30 emulator only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Never touch a personal handset",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = AppPrefs.get(context);
        assertFalse("Fixture requires no active session", prefs.getBoolean("active", false));
        assertFalse("Fixture requires a stopped Service", serviceRunning(context));
        Map<String, ?> originalPreferences = new HashMap<>(prefs.getAll());
        boolean originalDatabaseExists = context.getDatabasePath("sessions.db").exists();
        long wall = System.currentTimeMillis() - 3_000L;

        try (TrackerDb db = new TrackerDb(context)) {
            long before = sessionRows(db);
            long syntheticId = db.newSession(wall);
            try {
                RunState saved = new RunState();
                saved.id = syntheticId;
                saved.startedWallMs = wall;
                saved.startedElapsedMs = SystemClock.elapsedRealtime() - 2_000L;
                saved.distanceM = 12.5;
                saved.checkpointInterval = 1_000;
                saved.save(prefs, true);

                assertTrue(prefs.getBoolean("active", false));
                assertEquals(syntheticId, prefs.getLong("active_id", 0));

                Intent stop = new Intent(context, TrackingService.class)
                        .setAction(TrackingService.STOP);
                try (ActivityScenario<MainActivity> foreground =
                        ActivityScenario.launch(MainActivity.class)) {
                    foreground.onActivity(
                            activity -> {
                                ComponentName started = activity.startService(stop);
                                assertNotNull(started);
                                assertEquals(
                                        TrackingService.class.getName(),
                                        started.getClassName());
                            });

                    long deadline = SystemClock.uptimeMillis() + 7_000L;
                    while ((prefs.getBoolean("active", true)
                                    || serviceRunning(context))
                            && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(50);
                    }
                    assertFalse("Early STOP must clear active", prefs.getBoolean("active", true));
                    assertFalse("Early STOP must stop Service", serviceRunning(context));
                }

                assertEquals("Early STOP clears active_id", 0L, prefs.getLong("active_id", -1));
                TrackerDb.Entry completed = db.session(syntheticId);
                assertNotNull("Original synthetic row must survive", completed);
                assertEquals("COMPLETED", completed.status);
                assertEquals(12.5, completed.distance, 0.000001);
                assertEquals(
                        "Early STOP must not insert a duplicate session", before + 1,
                        sessionRows(db));
            } finally {
                // Explicit cleanup is restricted to the disposable emulator.
                if (serviceRunning(context)) {
                    context.stopService(new Intent(context, TrackingService.class));
                    long deadline = SystemClock.uptimeMillis() + 5_000L;
                    while (serviceRunning(context)
                            && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(50);
                    }
                }
                prefs.edit().putBoolean("active", false).commit();
                assertTrue(
                        "Remove only the synthetic session row",
                        db.deleteSession(syntheticId));
                restorePreferences(prefs, originalPreferences);
            }
        }
        if (!originalDatabaseExists) {
            assertTrue("Delete the synthetic-only SQLite file", context.deleteDatabase("sessions.db"));
        }
    }
}
