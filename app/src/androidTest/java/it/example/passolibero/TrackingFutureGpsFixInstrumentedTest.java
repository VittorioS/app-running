package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.lang.reflect.Field;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class TrackingFutureGpsFixInstrumentedTest {
    @Test
    public void futureFixLeavesTrackingStateUntouchedAndTheNextValidFixIsAccepted()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String prefsName = "test-future-gps-" + UUID.randomUUID();
        SharedPreferences preferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        try {
            TrackingService service = new TrackingService();
            RunState state = new RunState();
            state.startedElapsedMs = SystemClock.elapsedRealtime();
            state.gpsStatus = "Attesa";
            setField(service, "prefs", preferences);
            setField(service, "profile", AppPrefs.loadProfile(preferences));
            setField(service, "state", state);
            // Keep this callback fixture isolated from SQLite, TTS and live services.
            setField(service, "lastSavedDbElapsed", Long.MAX_VALUE);
            setField(service, "lastPointWall", Long.MAX_VALUE);

            service.onLocationChanged(fix(SystemClock.elapsedRealtimeNanos() + 5_000_000_000L));
            assertFalse(state.lastLocationValid);
            assertEquals("Attesa", state.gpsStatus);
            assertEquals(-1L, field("lastNormalGpsFixMs").getLong(service));

            long validNanos = SystemClock.elapsedRealtimeNanos();
            service.onLocationChanged(fix(validNanos));
            assertTrue(state.lastLocationValid);
            assertEquals(validNanos / 1_000_000L, field("lastNormalGpsFixMs").getLong(service));

            service.onLocationChanged(fix(SystemClock.elapsedRealtimeNanos() + 5_000_000_000L));
            assertEquals(validNanos / 1_000_000L, field("lastNormalGpsFixMs").getLong(service));
            long nextNanos = SystemClock.elapsedRealtimeNanos();
            service.onLocationChanged(fix(nextNanos));
            assertEquals(nextNanos / 1_000_000L, field("lastNormalGpsFixMs").getLong(service));
        } finally {
            context.deleteSharedPreferences(prefsName);
        }
    }

    private static Location fix(long timestampNanos) {
        Location fix = new Location("gps");
        fix.setElapsedRealtimeNanos(timestampNanos);
        fix.setTime(System.currentTimeMillis());
        fix.setLatitude(0);
        fix.setLongitude(0);
        fix.setAccuracy(5);
        fix.setSpeed(0);
        return fix;
    }

    private static Field field(String name) throws ReflectiveOperationException {
        Field field = TrackingService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void setField(TrackingService service, String name, Object value)
            throws ReflectiveOperationException {
        field(name).set(service, value);
    }
}
