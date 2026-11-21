package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.location.LocationManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * P5a/#26 G1: the legacy remove-then-request GPS registration sequence before extraction.
 *
 * <p>Uses fake calls only. No LocationManager registration, GPS permissions or real hardware.
 */
@RunWith(AndroidJUnit4.class)
public final class GpsUpdatesRegistrationCharacterizationTest {
    private interface LegacyCalls {
        void removeUpdates();

        void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM);
    }

    private static void originalRegistration(LegacyCalls calls, boolean fast) {
        calls.removeUpdates();
        calls.requestLocationUpdates(LocationManager.GPS_PROVIDER, fast ? 200L : 1_000L, 0f);
    }

    private static void originalFallback(LegacyCalls calls, boolean fast) {
        try {
            originalRegistration(calls, fast);
        } catch (SecurityException | IllegalArgumentException ex) {
            if (fast) {
                originalFallback(calls, false);
            }
        }
    }

    private static final class Recorder implements LegacyCalls {
        final List<String> events = new ArrayList<>();
        boolean failOnRemove;
        boolean failFastRequest;
        boolean failNormalRequest;

        @Override
        public void removeUpdates() {
            events.add("remove");
            if (failOnRemove) {
                throw new SecurityException("synthetic remove failure");
            }
        }

        @Override
        public void requestLocationUpdates(
                String provider, long minTimeMs, float minDistanceM) {
            events.add("request:" + provider + ":" + minTimeMs + ":" + minDistanceM);
            if ((minTimeMs == 200L && failFastRequest)
                    || (minTimeMs == 1_000L && failNormalRequest)) {
                throw new IllegalArgumentException("synthetic request failure");
            }
        }
    }

    @Test
    public void normalAndSprintRequestsAlwaysRemoveFirstAndUseExistingCadences() {
        Recorder normal = new Recorder();
        originalRegistration(normal, false);
        assertEquals(
                List.of("remove", "request:gps:1000:0.0"),
                normal.events);

        Recorder fast = new Recorder();
        originalRegistration(fast, true);
        assertEquals(
                List.of("remove", "request:gps:200:0.0"),
                fast.events);
    }

    @Test
    public void removeFailureStopsBeforeARequest() {
        Recorder recorder = new Recorder();
        recorder.failOnRemove = true;
        try {
            originalRegistration(recorder, true);
            fail("A failed unregister must propagate before request");
        } catch (SecurityException expected) {
            assertEquals(List.of("remove"), recorder.events);
        }
    }

    @Test
    public void failedFastRequestRetriesNormalRateWithAnotherRemove() {
        Recorder recorder = new Recorder();
        recorder.failFastRequest = true;
        originalFallback(recorder, true);
        assertEquals(
                List.of(
                        "remove",
                        "request:gps:200:0.0",
                        "remove",
                        "request:gps:1000:0.0"),
                recorder.events);
    }

    @Test
    public void failedNormalRequestDoesNotRetryAgain() {
        Recorder recorder = new Recorder();
        recorder.failNormalRequest = true;
        originalFallback(recorder, false);
        assertEquals(
                List.of("remove", "request:gps:1000:0.0"),
                recorder.events);
    }
}
