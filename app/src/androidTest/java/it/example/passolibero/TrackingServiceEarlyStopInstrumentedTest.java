package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.test.core.app.ServiceScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * P4a/#24 G1: a small real-Service characterization, not a simulated dispatch trace.
 *
 * <p>Only runs on the disposable API 30 CI emulator. It does not request permissions,
 * start tracking, create sessions, modify user preferences, or exercise GPS hardware.
 * START/recovery, queued START/STOP races and process death require separate tests.
 */
@RunWith(AndroidJUnit4.class)
public final class TrackingServiceEarlyStopInstrumentedTest {
    @Test
    public void stopWithoutAnActiveSessionDoesNotCreateTrackingOrPersistedHistory() {
        assumeTrue("This test is scoped to Android 11/API 30", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Do not run lifecycle tests against a personal handset",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = AppPrefs.get(context);

        // Avoid affecting another test's session. This is a read-only precondition,
        // not a request to clear, rewrite, or revoke anything.
        assumeFalse("An existing session must not be interrupted", prefs.getBoolean("active", false));
        boolean hadDatabase = context.getDatabasePath("sessions.db").exists();
        boolean hadActiveId = prefs.contains("active_id");
        long originalActiveId = hadActiveId ? prefs.getLong("active_id", 0L) : 0L;

        Intent stop = new Intent(context, TrackingService.class).setAction(TrackingService.STOP);
        try (ServiceScenario<TrackingService> scenario = ServiceScenario.launchService(stop)) {
            assertNotNull(scenario);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }

        assertFalse("STOP without a session must not start tracking", prefs.getBoolean("active", false));
        assertEquals("STOP must not change the active session marker", hadActiveId, prefs.contains("active_id"));
        if (hadActiveId) {
            assertEquals(originalActiveId, prefs.getLong("active_id", 0L));
        }
        assertEquals(
                "STOP without an active session must not create a SQLite history file",
                hadDatabase,
                context.getDatabasePath("sessions.db").exists());
    }
}
