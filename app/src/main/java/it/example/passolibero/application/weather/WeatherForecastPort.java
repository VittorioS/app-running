package it.example.passolibero.application.weather;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;

/** Read-only hourly forecast contract, independent of Android and HTTP. */
public interface WeatherForecastPort {
    Forecast forecast(double latitude, double longitude) throws IOException;

    /** Values already validated by the existing Open-Meteo client. */
    final class Forecast {
        public final List<Hour> hours;
        public final ZoneId zone;

        public Forecast(List<Hour> hours, ZoneId zone) {
            this.hours = hours;
            this.zone = zone;
        }
    }

    /** Exact numeric fields and epoch seconds supplied by the legacy hourly forecast. */
    final class Hour {
        public final long epochSec;
        public final double temperatureC;
        public final double windKmh;

        public Hour(long epochSec, double temperatureC, double windKmh) {
            this.epochSec = epochSec;
            this.temperatureC = temperatureC;
            this.windKmh = windKmh;
        }
    }
}
