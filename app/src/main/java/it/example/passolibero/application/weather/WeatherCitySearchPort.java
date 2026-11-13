package it.example.passolibero.application.weather;

import java.io.IOException;
import java.util.List;

/** Read-only city search contract, independent of Android and HTTP libraries. */
public interface WeatherCitySearchPort {
    List<City> search(String query) throws IOException;

    /** Immutable legacy city result; title and coordinates are preserved verbatim. */
    final class City {
        public final String title;
        public final double latitude;
        public final double longitude;

        public City(String title, double latitude, double longitude) {
            this.title = title;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
