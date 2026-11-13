package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/**
 * P7a/#31: characterize the exact legacy local-time and DST behavior before extraction.
 *
 * <p>These are contract tests, including how the old algorithm handles repeated fall-back time.
 */
public final class WeatherScheduleClockCharacterizationTest {
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private static long local(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ROME)
                .toInstant()
                .toEpochMilli();
    }

    @Test
    public void picksFirstStrictlyFutureLocalSlotAndTomorrowAfterLastSlot() {
        assertEquals(
                local(2026, 11, 6, 6, 0),
                WeatherSchedule.nextEpochMillis(
                        Arrays.asList("18:00", "06:00", "11:00"),
                        ROME,
                        local(2026, 11, 6, 5, 0)));
        assertEquals(
                local(2026, 11, 6, 11, 0),
                WeatherSchedule.nextEpochMillis(
                        Arrays.asList("06:00", "11:00", "18:00"),
                        ROME,
                        local(2026, 11, 6, 6, 0)));
        assertEquals(
                local(2026, 11, 7, 6, 0),
                WeatherSchedule.nextEpochMillis(
                        Arrays.asList("06:00", "11:00", "18:00"),
                        ROME,
                        local(2026, 11, 6, 19, 0)));
    }

    @Test
    public void emptySlotsDoNotScheduleAnything() {
        assertEquals(-1L, WeatherSchedule.nextEpochMillis(null, ROME, 0));
        assertEquals(
                -1L,
                WeatherSchedule.nextEpochMillis(Collections.emptyList(), ROME, 0));
    }

    @Test
    public void springForwardMissingLocalHourIsShiftedToThreeThirty() {
        assertEquals(
                local(2027, 3, 28, 3, 30),
                WeatherSchedule.nextEpochMillis(
                        Collections.singletonList("02:30"),
                        ROME,
                        local(2027, 3, 28, 0, 30)));
    }

    @Test
    public void fallBackRetainsOldEarlierOffsetPolicyWithoutSecondTrigger() {
        long nowDuringSecondTwoAm =
                LocalDateTime.of(2026, 10, 25, 2, 0)
                        .atOffset(ZoneOffset.ofHours(1))
                        .toInstant()
                        .toEpochMilli();
        long nextDay = local(2026, 10, 26, 2, 30);
        long scheduled =
                WeatherSchedule.nextEpochMillis(
                        Collections.singletonList("02:30"), ROME, nowDuringSecondTwoAm);
        assertEquals(
                "The legacy scheduler does not repeat the second 02:30 after DST fall-back",
                nextDay,
                scheduled);
        assertTrue(scheduled > nowDuringSecondTwoAm);
    }

    @Test
    public void selectedZoneIsNotSystemDefaultZone() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        long now = Instant.parse("2026-11-06T05:10:00Z").toEpochMilli();
        long result =
                WeatherSchedule.nextEpochMillis(
                        Arrays.asList("06:00", "18:00"), tokyo, now);
        assertEquals(
                LocalDateTime.of(2026, 11, 6, 18, 0)
                        .atZone(tokyo)
                        .toInstant()
                        .toEpochMilli(),
                result);
    }
}
