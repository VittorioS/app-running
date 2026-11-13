package it.example.passolibero.data.remote;

import it.example.passolibero.WeatherApi;
import it.example.passolibero.application.weather.WeatherCitySearchPort;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapt the unchanged Open-Meteo geocoding client to an application read port.
 *
 * <p>The injectable source allows offline mapping tests; production still uses the exact
 * same WeatherApi.findCities HTTPS request, timeouts and six-result limit.
 */
public final class OpenMeteoCitySearchAdapter implements WeatherCitySearchPort {
    @FunctionalInterface
    public interface CitySource {
        List<WeatherApi.City> findCities(String query) throws IOException;
    }

    private final CitySource source;

    public OpenMeteoCitySearchAdapter() {
        this(WeatherApi::findCities);
    }

    public OpenMeteoCitySearchAdapter(CitySource source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public List<City> search(String query) throws IOException {
        List<WeatherApi.City> found = source.findCities(query);
        List<City> results = new ArrayList<>(found.size());
        for (WeatherApi.City city : found) {
            results.add(new City(city.title, city.latitude, city.longitude));
        }
        return results;
    }
}
