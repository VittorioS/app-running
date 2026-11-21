package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.location.Location;
import android.location.LocationManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import it.example.passolibero.domain.GpsFixPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * P5a/#26 G1: the exact monotonic timestamp expressions in TrackingService before extraction.
 *
 * <p>These are synthetic Android Location objects, not real GPS or screen-off hardware tests.
 */
@RunWith(AndroidJUnit4.class)
public final class LocationFixTimestampCharacterizationTest {
    private static Location fix(long elapsedNanos) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setElapsedRealtimeNanos(elapsedNanos);
        location.setTime(1_730_000_000_000L);
        location.setAccuracy(5f);
        return location;
    }

    @Test
    public void normalFixUsesTruncatedMonotonicMillisecondsForSprintThrottle() {
        Location location = fix(1_900_999_999L);
        long legacyFixMs = location.getElapsedRealtimeNanos() / 1_000_000L;

        assertEquals(1_900L, legacyFixMs);
        assertTrue(GpsFixPolicy.skipNormalFix(true, 1_001L, legacyFixMs));
        assertFalse(GpsFixPolicy.skipNormalFix(true, 1_000L, legacyFixMs));
        assertFalse(GpsFixPolicy.skipNormalFix(false, 1_001L, legacyFixMs));
    }

    @Test
    public void absoluteAgeDoesNotRewriteFutureFixesAsPastFixes() {
        Location previous = fix(4_999_999_999L);
        Location future = fix(6_001_000_000L);
        long nowMs = 5_000L;

        long previousAgeMs =
                Math.abs(nowMs - previous.getElapsedRealtimeNanos() / 1_000_000L);
        long futureAgeMs = Math.abs(nowMs - future.getElapsedRealtimeNanos() / 1_000_000L);
        assertEquals(1L, previousAgeMs);
        assertEquals(1_001L, futureAgeMs);
        assertTrue(MovementClassifier.isFreshMeasurement(nowMs, 4_999L));
        assertFalse(MovementClassifier.isFreshMeasurement(nowMs, 6_001L));
    }

    @Test
    public void gpsDeltaSubtractsNanosecondsBeforeConvertingToSeconds() {
        Location first = fix(9_876_543_210L);
        Location second = fix(10_987_654_321L);
        double legacyDeltaSeconds =
                (second.getElapsedRealtimeNanos() - first.getElapsedRealtimeNanos()) / 1e9;
        assertEquals(1.111111111, legacyDeltaSeconds, 0.000000000001);

        Location duplicate = fix(9_876_543_210L);
        Location stale = fix(9_000_000_000L);
        assertEquals(
                0.0,
                (duplicate.getElapsedRealtimeNanos() - first.getElapsedRealtimeNanos()) / 1e9,
                0);
        assertTrue(
                (stale.getElapsedRealtimeNanos() - first.getElapsedRealtimeNanos()) / 1e9 < 0);
    }

    @Test
    public void wallClockIsIndependentFromMonotonicGpsFixTimestamp() {
        Location location = fix(2_000_999_999L);
        long legacyFixMs = location.getElapsedRealtimeNanos() / 1_000_000L;
        long wallMs = location.getTime();

        location.setTime(wallMs + 86_400_000L);
        assertEquals(2_000L, location.getElapsedRealtimeNanos() / 1_000_000L);
        assertEquals(legacyFixMs, location.getElapsedRealtimeNanos() / 1_000_000L);
        assertEquals(wallMs + 86_400_000L, location.getTime());
    }

    @Test
    public void adapterRetainsLegacyMillisAndAgeAtGpsClockBoundaries() {
        for (long elapsedNanos :
                new long[] {0L, 999_999L, 1_000_000L, 1_900_999_999L, 6_001_000_000L}) {
            Location location = fix(elapsedNanos);
            assertEquals(
                    elapsedNanos / 1_000_000L,
                    it.example.passolibero.platform.location.LocationFixTimestampMapper.elapsedMs(
                            location));
            for (long nowMs : new long[] {0L, 1_900L, 5_000L, 6_002L}) {
                assertEquals(
                        Math.abs(nowMs - elapsedNanos / 1_000_000),
                        it.example.passolibero.platform.location.LocationFixTimestampMapper
                                .absoluteAgeMs(nowMs, location));
            }
        }
    }

    @Test
    public void adapterKeepsNanosecondDeltaBeforeDividingEvenForStaleFixes() {
        Location previous = fix(9_876_543_210L);
        for (long elapsedNanos : new long[] {9_000_000_000L, 9_876_543_210L, 10_987_654_321L}) {
            Location current = fix(elapsedNanos);
            double expected =
                    (current.getElapsedRealtimeNanos() - previous.getElapsedRealtimeNanos()) / 1e9;
            assertEquals(
                    expected,
                    it.example.passolibero.platform.location.LocationFixTimestampMapper.deltaSeconds(
                            current, previous),
                    0);
        }
    }
}
