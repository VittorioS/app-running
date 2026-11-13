package it.example.passolibero.data.remote;

import it.example.passolibero.WeatherApi;
import it.example.passolibero.application.weather.WeatherForecastPort;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Map the unchanged hourly Open-Meteo forecast into the application read contract.
 *
 * <p>Production still uses WeatherApi.forecast, with its existing URL, timeouts,
 * validation and timezone fallback. An injected source supports offline tests.
 */
public final class OpenMeteoForecastAdapter implements WeatherForecastPort {
    @FunctionalInterface
    public interface ForecastSource {
        WeatherApi.Forecast forecast(double latitude, double longitude) throws IOException;
    }

    private final ForecastSource source;

    public OpenMeteoForecastAdapter() {
        this(WeatherApi::forecast);
    }

    public OpenMeteoForecastAdapter(ForecastSource source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public Forecast forecast(double latitude, double longitude) throws IOException {
        WeatherApi.Forecast raw = source.forecast(latitude, longitude);
        List<Hour> hours = new ArrayList<>(raw.hours.size());
        for (WeatherApi.Hour hour : raw.hours) {
            hours.add(new Hour(hour.epochSec, hour.temperatureC, hour.windKmh));
        }
        return new Forecast(hours, raw.zone);
    }
}
