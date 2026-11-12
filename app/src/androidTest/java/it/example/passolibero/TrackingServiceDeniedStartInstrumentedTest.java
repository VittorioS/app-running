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
import android.os.Build;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * P4a/#24 G1: characterize the real Service's existing denied-location START path.
 *
 * <p>Runs only on a disposable API 30 emulator with location already denied. Never requests
 * or revokes permissions, enables GPS, or touches existing active sessions. It does not
 * exercise a successful foreground start, worker initialization, or process death.
 */
@RunWith(AndroidJUnit4.class)
public final class TrackingServiceDeniedStartInstrumentedTest {
    private static final String LOCATION_ERROR =
            "Attiva il GPS e consenti la posizione precisa.";

    @SuppressWarnings("deprecation")
    private static boolean trackingServiceRunning(Context context) {
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(50)) {
            if (TrackingService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void startWithDeniedPreciseLocationStopsWithoutCreatingSession() {
        assumeTrue("Requires Android 11/API 30", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Never exercise a lifecycle test on a personal phone",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = AppPrefs.get(context);

        assertTrue(
                "Location must be denied by the existing emulator configuration",
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED);
        assertFalse("The disposable emulator must not already have an active session", prefs.getBoolean("active", false));
        assertFalse("The disposable emulator must not have a running Service", trackingServiceRunning(context));

        boolean hadId = prefs.contains("active_id");
        long originalId = hadId ? prefs.getLong("active_id", 0L) : 0L;
        boolean hadDatabase = context.getDatabasePath("sessions.db").exists();
        boolean hadError = prefs.contains("error");
        String originalError = hadError ? prefs.getString("error", null) : null;

        // Remove only the prior UI error so that the new error proves onStartCommand
        // actually ran; restore this test fixture immediately afterward.
        prefs.edit().remove("error").commit();
        Intent start = new Intent(context, TrackingService.class).setAction(TrackingService.START);
        try {
            try (ActivityScenario<MainActivity> foreground =
                    ActivityScenario.launch(MainActivity.class)) {
                foreground.onActivity(
                        activity -> {
                            ComponentName launched = activity.startService(start);
                            assertNotNull("START should be delivered to the Service", launched);
                            assertEquals(
                                    TrackingService.class.getName(), launched.getClassName());
                        });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();

                // Service denial is asynchronous relative to Activity.startService.
                long deadline = SystemClock.uptimeMillis() + 5_000L;
                while (!LOCATION_ERROR.equals(prefs.getString("error", null))
                        && SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(50L);
                }
                assertEquals(
                        "Denied START must report the existing permission/GPS error",
                        LOCATION_ERROR,
                        prefs.getString("error", null));

                while (trackingServiceRunning(context)
                        && SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(50L);
                }
                assertFalse(
                        "Denied START must not leave a Service running",
                        trackingServiceRunning(context));
            }

            assertFalse(
                    "Denied START must not activate tracking",
                    prefs.getBoolean("active", false));
            assertEquals(
                    "Denied START must not change the active ID marker",
                    hadId,
                    prefs.contains("active_id"));
            if (hadId) {
                assertEquals(originalId, prefs.getLong("active_id", 0L));
            }
            assertEquals(
                    "Denied START must not create a history database",
                    hadDatabase,
                    context.getDatabasePath("sessions.db").exists());
        } finally {
            // The test changes only the UI error key on a disposable emulator.
            SharedPreferences.Editor restore = prefs.edit();
            if (hadError) {
                restore.putString("error", originalError);
            } else {
                restore.remove("error");
            }
            restore.commit();
        }
    }
}
