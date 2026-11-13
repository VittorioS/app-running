package it.example.passolibero;

import it.example.passolibero.application.weather.SearchWeatherCities;
import it.example.passolibero.application.weather.WeatherCitySearchPort;
import it.example.passolibero.data.remote.OpenMeteoCitySearchAdapter;
import java.io.IOException;
import java.util.List;

/** Legacy composition boundary for the WeatherSettingsActivity city search. */
public final class WeatherCitySearchBridge {
    private WeatherCitySearchBridge() {}

    public static List<WeatherCitySearchPort.City> search(String query) throws IOException {
        return new SearchWeatherCities(new OpenMeteoCitySearchAdapter()).execute(query);
    }
}
