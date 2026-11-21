package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import android.location.Location;
import android.location.LocationManager;

import it.example.passolibero.platform.location.GpsFixCallbackIngress;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * P5a/#26 G1: characterize the legacy onLocationChanged entry guard before extracting it.
 *
 * <p>These are synthetic Location callbacks on API 30, not actual GPS hardware tests.
 */
@RunWith(AndroidJUnit4.class)
public final class GpsFixCallbackIngressCharacterizationTest {
    private static Location fix(boolean hasAccuracy, long elapsedNanos) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setElapsedRealtimeNanos(elapsedNanos);
        if (hasAccuracy) {
            location.setAccuracy(5f);
        }
        return location;
    }

    /** Exact legacy onLocationChanged guard, parameterizing only its session state. */
    private static void originalDispatch(
            boolean hasActiveSession, Location location, Consumer<Location> accepted) {
        if (!hasActiveSession || !location.hasAccuracy()) return;
        accepted.accept(location);
    }

    @Test
    public void inactiveSessionDiscardsEvenNullFixWithoutReadingItsAccuracy() {
        List<Location> forwarded = new ArrayList<>();
        originalDispatch(false, fix(true, 5_000_000_000L), forwarded::add);
        originalDispatch(false, null, forwarded::add);
        assertEquals(0, forwarded.size());
    }

    @Test
    public void activeSessionOnlyForwardsFixesWithAccuracyOnceAndByIdentity() {
        List<Location> forwarded = new ArrayList<>();
        Location noAccuracy = fix(false, 1_000_000_000L);
        Location accurate = fix(true, 2_000_000_000L);
        originalDispatch(true, noAccuracy, forwarded::add);
        originalDispatch(true, accurate, forwarded::add);
        assertEquals(1, forwarded.size());
        assertSame(accurate, forwarded.get(0));
    }

    @Test
    public void callbackIngressDoesNotReorderOrValidateStaleAndFutureTimestamps() {
        List<Location> forwarded = new ArrayList<>();
        Location newer = fix(true, 9_000_000_000L);
        Location stale = fix(true, 8_000_000_000L);
        Location future = fix(true, 20_000_000_000L);

        originalDispatch(true, newer, forwarded::add);
        originalDispatch(true, stale, forwarded::add);
        originalDispatch(true, future, forwarded::add);

        assertEquals(3, forwarded.size());
        assertSame(newer, forwarded.get(0));
        assertSame(stale, forwarded.get(1));
        assertSame(future, forwarded.get(2));
    }

    @Test
    public void activeNullFixAndCallbackFailureAreNotSilentlySwallowed() {
        try {
            originalDispatch(true, null, ignored -> {});
            fail("Original guard dereferences a null fix for an active session");
        } catch (NullPointerException expected) {
            // Keep the exact absence of a null-fix fallback.
        }
        IllegalStateException problem = new IllegalStateException("synthetic");
        try {
            originalDispatch(true, fix(true, 1_000_000L), ignored -> { throw problem; });
            fail("Original callback propagates its handler failure");
        } catch (IllegalStateException expected) {
            assertSame(problem, expected);
        }
    }

    @Test
    public void extractedIngressMatchesLegacyForInactiveMissingAccuracyAndAcceptedFixes() {
        for (boolean active : new boolean[] {false, true}) {
            for (boolean hasAccuracy : new boolean[] {false, true}) {
                Location location = fix(hasAccuracy, 7_000_000_000L);
                List<Location> legacy = new ArrayList<>();
                List<Location> extracted = new ArrayList<>();
                originalDispatch(active, location, legacy::add);
                GpsFixCallbackIngress.dispatch(active, location, extracted::add);
                assertEquals(legacy.size(), extracted.size());
                if (!legacy.isEmpty()) {
                    assertSame(legacy.get(0), extracted.get(0));
                }
            }
        }
    }

    @Test
    public void extractedIngressStillPreservesStaleFutureOrderAndListenerErrors() {
        List<Location> observed = new ArrayList<>();
        Location future = fix(true, 50_000_000_000L);
        Location stale = fix(true, 10_000_000_000L);
        GpsFixCallbackIngress.dispatch(true, future, observed::add);
        GpsFixCallbackIngress.dispatch(true, stale, observed::add);
        assertEquals(2, observed.size());
        assertSame(future, observed.get(0));
        assertSame(stale, observed.get(1));

        IllegalStateException problem = new IllegalStateException("callback");
        try {
            GpsFixCallbackIngress.dispatch(
                    true, fix(true, 1_000_000L), ignored -> { throw problem; });
            fail("The Android callback must not swallow processing failures");
        } catch (IllegalStateException expected) {
            assertSame(problem, expected);
        }
    }

    @Test
    public void extractedIngressRetainsNullGuardShortCircuitAndActiveNullFailure() {
        GpsFixCallbackIngress.dispatch(false, null, ignored -> {
            fail("Inactive callbacks must not be forwarded");
        });
        try {
            GpsFixCallbackIngress.dispatch(true, null, ignored -> {});
            fail("Active null fix must still fail at the legacy accuracy access");
        } catch (NullPointerException expected) {
            // The Service did not treat a null fix as missing GPS evidence.
        }
    }
}
