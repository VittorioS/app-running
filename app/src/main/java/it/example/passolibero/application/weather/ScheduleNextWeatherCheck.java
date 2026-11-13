package it.example.passolibero.application.weather;

import it.example.passolibero.domain.weather.NextWeatherAlarmPolicy;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Choose the next scheduled weather check using an injected clock and its local time zone.
 *
 * <p>The platform layer still owns alarms, jobs and permissions. Nothing is scheduled here.
 */
public final class ScheduleNextWeatherCheck {
    private final Clock clock;

    public ScheduleNextWeatherCheck(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public long nextEpochMillis(List<String> times) {
        return NextWeatherAlarmPolicy.nextEpochMillis(times, clock.getZone(), clock.millis());
    }
}
