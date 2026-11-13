package it.example.passolibero;

import it.example.passolibero.application.weather.LoadWeatherPreviewForecast;
import it.example.passolibero.application.weather.WeatherForecastPort;
import it.example.passolibero.data.remote.OpenMeteoForecastAdapter;
import java.io.IOException;

/** Legacy composition boundary for manual WeatherSettingsActivity forecast previews. */
public final class WeatherForecastBridge {
    private WeatherForecastBridge() {}

    public static WeatherForecastPort.Forecast preview(double latitude, double longitude)
            throws IOException {
        return new LoadWeatherPreviewForecast(new OpenMeteoForecastAdapter())
                .execute(latitude, longitude);
    }
}
