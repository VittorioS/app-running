package it.example.passolibero.application.weather;

import static org.junit.Assert.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** P7a/#31: fake-clock contract for the single weather-alarm calculation use case. */
public final class ScheduleNextWeatherCheckTest {
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private static Clock atLocal(int year, int month, int day, int hour, int minute) {
        Instant instant = LocalDateTime.of(year, month, day, hour, minute).atZone(ROME).toInstant();
        return Clock.fixed(instant, ROME);
    }

    private static long atUtc(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    @Test
    public void fixedClockChoosesUpcomingSlotInPhoneLocalTime() {
        ScheduleNextWeatherCheck subject = new ScheduleNextWeatherCheck(atLocal(2026, 11, 6, 5, 0));
        assertEquals(
                atUtc("2026-11-06T05:00:00Z"),
                subject.nextEpochMillis(Arrays.asList("18:00", "06:00", "11:00")));
    }

    @Test
    public void exactSlotIsNotScheduledAgainUntilNextDay() {
        ScheduleNextWeatherCheck subject = new ScheduleNextWeatherCheck(atLocal(2026, 11, 6, 6, 0));
        assertEquals(
                atUtc("2026-11-07T05:00:00Z"),
                subject.nextEpochMillis(Collections.singletonList("06:00")));
    }

    @Test
    public void changingOnlyClockChangesNextAlarmDeterministically() {
        assertEquals(
                atUtc("2026-11-06T17:00:00Z"),
                new ScheduleNextWeatherCheck(atLocal(2026, 11, 6, 8, 0))
                        .nextEpochMillis(Arrays.asList("06:00", "18:00")));
        assertEquals(
                atUtc("2026-11-07T05:00:00Z"),
                new ScheduleNextWeatherCheck(atLocal(2026, 11, 6, 19, 0))
                        .nextEpochMillis(Arrays.asList("06:00", "18:00")));
    }

    @Test
    public void emptyScheduleNeverCallsAndroidAlarmCode() {
        ScheduleNextWeatherCheck subject =
                new ScheduleNextWeatherCheck(Clock.fixed(Instant.EPOCH, ROME));
        assertEquals(-1L, subject.nextEpochMillis(null));
        assertEquals(-1L, subject.nextEpochMillis(Collections.emptyList()));
    }

    @Test
    public void fixedClockRespectsSpringForwardWithoutInventingLocalHour() {
        ScheduleNextWeatherCheck subject =
                new ScheduleNextWeatherCheck(atLocal(2027, 3, 28, 0, 30));
        long expected = atUtc("2027-03-28T01:30:00Z");
        assertEquals(expected, subject.nextEpochMillis(Collections.singletonList("02:30")));
    }
}
