package it.example.passolibero.application.weather;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Look up cities once, preserving source ordering, values and errors. */
public final class SearchWeatherCities {
    private final WeatherCitySearchPort citySearch;

    public SearchWeatherCities(WeatherCitySearchPort citySearch) {
        this.citySearch = Objects.requireNonNull(citySearch);
    }

    public List<WeatherCitySearchPort.City> execute(String query) throws IOException {
        return citySearch.search(query);
    }
}
