package it.example.passolibero.domain.weather;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Pure scheduling rule for the next local daily weather check.
 *
 * <p>Preserves the legacy resolution of daylight saving gaps and overlaps, including the earlier
 * offset selected by atZone for an ambiguous fall-back local time.
 */
public final class NextWeatherAlarmPolicy {
    private NextWeatherAlarmPolicy() {}

    /** Returns the strictly future local slot, or -1 for an empty schedule. */
    public static long nextEpochMillis(List<String> times, ZoneId zone, long nowMillis) {
        if (times == null || times.isEmpty()) return -1;
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        long best = Long.MAX_VALUE;
        for (int days = 0; days <= 2; days++) {
            LocalDate date = today.plusDays(days);
            for (String hhmm : times) {
                LocalTime time = LocalTime.parse(hhmm);
                long candidate = date.atTime(time).atZone(zone).toInstant().toEpochMilli();
                // Legacy contract: an exact or already passed slot is not scheduled again.
                if (candidate > nowMillis && candidate < best) best = candidate;
            }
        }
        return best == Long.MAX_VALUE ? -1 : best;
    }
}
