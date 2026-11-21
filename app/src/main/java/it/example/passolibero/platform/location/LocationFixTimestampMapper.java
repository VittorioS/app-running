package it.example.passolibero.platform.location;

import android.location.Location;

/** Maps Android Location clocks without changing legacy GNSS callback arithmetic. */
public final class LocationFixTimestampMapper {
    private LocationFixTimestampMapper() {}

    /** Truncate fix elapsed-realtime nanoseconds to milliseconds as the Service did. */
    public static long elapsedMs(Location location) {
        return location.getElapsedRealtimeNanos() / 1_000_000L;
    }

    /** Preserve the absolute age used by the existing GPS quality check. */
    public static long absoluteAgeMs(long nowElapsedMs, Location location) {
        return Math.abs(nowElapsedMs - location.getElapsedRealtimeNanos() / 1_000_000);
    }

    /** Preserve subtraction in nanoseconds before conversion to floating-point seconds. */
    public static double deltaSeconds(Location current, Location previous) {
        return (current.getElapsedRealtimeNanos() - previous.getElapsedRealtimeNanos()) / 1e9;
    }
}
