package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
                boolean removedSyntheticRow = db.deleteSession(syntheticId);
                // Always restore the original preferences even if cleanup reports a failure.
                restorePreferences(prefs, originalPreferences);
                assertTrue("Remove only the synthetic session row", removedSyntheticRow);
            }
        }
        if (!originalDatabaseExists) {
            assertTrue("Delete the synthetic-only SQLite file", context.deleteDatabase("sessions.db"));
        }
    }


    /**
     * A persisted active session must remain recoverable when a real START
     * is rejected before foreground acquisition due to missing location access.
     * This does not simulate OS process death or successful Service recovery.
     */
    @Test
    public void deniedRestartPreservesPersistedActiveSessionForLaterRecovery() {
        assumeTrue("API 30 emulator only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Never touch a personal handset",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = AppPrefs.get(context);
        assertTrue(
                "Disposable emulator must already have precise location denied",
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED);
        assertFalse("Fixture requires no active session", prefs.getBoolean("active", false));
        assertFalse("Fixture requires a stopped Service", serviceRunning(context));

        Map<String, ?> originalPreferences = new HashMap<>(prefs.getAll());
        boolean originalDatabaseExists = context.getDatabasePath("sessions.db").exists();
        long startedWall = System.currentTimeMillis() - 5_000L;

        try (TrackerDb db = new TrackerDb(context)) {
            long before = sessionRows(db);
            long syntheticId = db.newSession(startedWall);
            try {
                RunState saved = new RunState();
                saved.id = syntheticId;
                saved.startedWallMs = startedWall;
                saved.startedElapsedMs = SystemClock.elapsedRealtime() - 4_000L;
                saved.distanceM = 24.75;
                saved.checkpointInterval = 1_000;
                saved.save(prefs, true);
                prefs.edit().remove("error").commit();

                Intent start = new Intent(context, TrackingService.class)
                        .setAction(TrackingService.START);
                try (ActivityScenario<MainActivity> foreground =
                        ActivityScenario.launch(MainActivity.class)) {
                    foreground.onActivity(
                            activity -> {
                                ComponentName launched = activity.startService(start);
                                assertNotNull("START must reach the real Service", launched);
                                assertEquals(
                                        TrackingService.class.getName(),
                                        launched.getClassName());
                            });

                    long deadline = SystemClock.uptimeMillis() + 7_000L;
                    while (!"Attiva il GPS e consenti la posizione precisa."
                                    .equals(prefs.getString("error", null))
                            && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(50L);
                    }
                    assertEquals(
                            "Rejected restart must report the original permission error",
                            "Attiva il GPS e consenti la posizione precisa.",
                            prefs.getString("error", null));
                    while (serviceRunning(context)
                            && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(50L);
                    }
                    assertFalse("Rejected restart must stop the Service", serviceRunning(context));
                }

                assertTrue("A failed restart must not finish an active session",
                        prefs.getBoolean("active", false));
                assertEquals("A failed restart must preserve the session ID",
                        syntheticId, prefs.getLong("active_id", 0L));
                RunState recovered = RunState.read(prefs);
                assertEquals(syntheticId, recovered.id);
                assertEquals(24.75, recovered.distanceM, 0.000001);

                TrackerDb.Entry entry = db.session(syntheticId);
                assertNotNull("Persisted session must survive rejected restart", entry);
                assertEquals("IN_PROGRESS", entry.status);
                assertEquals("No duplicate session may be inserted", before + 1,
                        sessionRows(db));
            } finally {
                if (serviceRunning(context)) {
                    context.stopService(new Intent(context, TrackingService.class));
                    long deadline = SystemClock.uptimeMillis() + 5_000L;
                    while (serviceRunning(context)
                            && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(50L);
                    }
                }
                prefs.edit().putBoolean("active", false).commit();
                boolean removedSyntheticRow = db.deleteSession(syntheticId);
                restorePreferences(prefs, originalPreferences);
                assertTrue("Remove only the synthetic session row", removedSyntheticRow);
            }
        }
        if (!originalDatabaseExists) {
            assertTrue("Delete synthetic-only SQLite file",
                    context.deleteDatabase("sessions.db"));
        }
    }


    /**
     * A legacy active flag with zero active_id still takes the early-STOP
     * hydration path. No valid SQLite session must be invented or completed.
     */
    @Test
    public void earlyStopWithActiveFlagAndZeroIdClearsFlagWithoutCreatingSession() {
        assumeTrue("API 30 emulator only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Never exercise lifecycle tests on a personal handset",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = AppPrefs.get(context);
        assertFalse("Fixture requires no active session", prefs.getBoolean("active", false));
        assertFalse("Fixture requires a stopped Service", serviceRunning(context));
        Map<String, ?> originalPreferences = new HashMap<>(prefs.getAll());
        boolean originalDatabaseExists = context.getDatabasePath("sessions.db").exists();

        try (TrackerDb db = new TrackerDb(context)) {
            long originalRows = sessionRows(db);
            try {
                RunState invalid = new RunState();
                invalid.id = 0L;
                invalid.startedWallMs = System.currentTimeMillis() - 2_000L;
                invalid.startedElapsedMs = SystemClock.elapsedRealtime() - 1_000L;
                invalid.checkpointInterval = 1_000;
                invalid.save(prefs, true);
                assertTrue("Invalid legacy marker starts as active", prefs.getBoolean("active", false));
                assertEquals(0L, prefs.getLong("active_id", -1L));

                Intent stop = new Intent(context, TrackingService.class)
                        .setAction(TrackingService.STOP);
                try (ActivityScenario<MainActivity> foreground =
                        ActivityScenario.launch(MainActivity.class)) {
                    foreground.onActivity(
                            activity -> {
                                ComponentName started = activity.startService(stop);
                                assertNotNull(started);
                                assertEquals(TrackingService.class.getName(), started.getClassName());
                            });

                    long deadline = SystemClock.uptimeMillis() + 7_000L;
                    while ((prefs.getBoolean("active", true) || serviceRunning(context))
                            && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(50L);
                    }
                    assertFalse("Early STOP must clear the invalid active marker",
                            prefs.getBoolean("active", true));
                    assertFalse("STOP must stop the real Service", serviceRunning(context));
                }

                assertEquals("No positive session ID may be invented", 0L,
                        prefs.getLong("active_id", -1L));
                assertEquals("No SQLite session may be created or completed",
                        originalRows, sessionRows(db));
            } finally {
                if (serviceRunning(context)) {
                    context.stopService(new Intent(context, TrackingService.class));
                    long deadline = SystemClock.uptimeMillis() + 5_000L;
                    while (serviceRunning(context)
                            && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(50L);
                    }
                }
                restorePreferences(prefs, originalPreferences);
            }
        }
        if (!originalDatabaseExists) {
            assertTrue("Delete the synthetic-only SQLite file",
                    context.deleteDatabase("sessions.db"));
        }
    }
}
