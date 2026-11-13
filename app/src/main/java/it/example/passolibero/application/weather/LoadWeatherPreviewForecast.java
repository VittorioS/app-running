package it.example.passolibero.application.weather;

import java.io.IOException;
import java.util.Objects;

/** Load one city preview without owning HTTP, UI rendering or concurrency decisions. */
public final class LoadWeatherPreviewForecast {
    private final WeatherForecastPort forecasts;

    public LoadWeatherPreviewForecast(WeatherForecastPort forecasts) {
        this.forecasts = Objects.requireNonNull(forecasts);
    }

    public WeatherForecastPort.Forecast execute(double latitude, double longitude)
            throws IOException {
        return forecasts.forecast(latitude, longitude);
    }
}
