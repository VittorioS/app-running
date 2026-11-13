package it.example.passolibero.application.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** ADR-002/#32 G1: deterministic contract for a weather-preview forecast lookup. */
public final class LoadWeatherPreviewForecastTest {
    private static final class FakeForecastPort implements WeatherForecastPort {
        int calls;
        double latitude;
        double longitude;
        Forecast response;
        IOException failure;

        @Override
        public Forecast forecast(double lat, double lon) throws IOException {
            calls++;
            latitude = lat;
            longitude = lon;
            if (failure != null) throw failure;
            return response;
        }
    }

    @Test
    public void preservesCoordinatesResponseIdentityHourOrderAndTimezone() throws IOException {
        FakeForecastPort source = new FakeForecastPort();
        WeatherForecastPort.Hour later = new WeatherForecastPort.Hour(200, 22.5, 10.0);
        WeatherForecastPort.Hour earlier = new WeatherForecastPort.Hour(100, 17.5, 4.0);
        source.response =
                new WeatherForecastPort.Forecast(
                        Arrays.asList(later, earlier), ZoneId.of("Europe/Rome"));

        WeatherForecastPort.Forecast actual =
                new LoadWeatherPreviewForecast(source).execute(40.68, 14.77);

        assertSame(source.response, actual);
        assertEquals(1, source.calls);
        assertEquals(40.68, source.latitude, 0);
        assertEquals(14.77, source.longitude, 0);
        assertSame(later, actual.hours.get(0));
        assertSame(earlier, actual.hours.get(1));
        assertEquals(22.5, actual.hours.get(0).temperatureC, 0);
        assertEquals(10.0, actual.hours.get(0).windKmh, 0);
        assertEquals(ZoneId.of("Europe/Rome"), actual.zone);
    }

    @Test
    public void preservesAnEmptyFakeForecastWithoutManufacturingHours() throws IOException {
        FakeForecastPort source = new FakeForecastPort();
        source.response =
                new WeatherForecastPort.Forecast(Collections.emptyList(), ZoneId.of("UTC"));

        WeatherForecastPort.Forecast result = new LoadWeatherPreviewForecast(source).execute(1, 2);

        assertSame(source.response, result);
        assertEquals(0, result.hours.size());
        assertEquals(1, source.calls);
    }

    @Test
    public void checkedIoFailureReachesExistingPreviewErrorPathUnchanged() {
        FakeForecastPort source = new FakeForecastPort();
        IOException offline = new IOException("offline");
        source.failure = offline;

        try {
            new LoadWeatherPreviewForecast(source).execute(4, 5);
            fail("Network failures must not be hidden as an empty forecast");
        } catch (IOException caught) {
            assertSame(offline, caught);
        }
        assertEquals(1, source.calls);
    }

    @Test
    public void noExtraCallsOrNormalizationOfLegacyCoordinates() throws IOException {
        FakeForecastPort source = new FakeForecastPort();
        source.response =
                new WeatherForecastPort.Forecast(Collections.emptyList(), ZoneId.of("UTC"));
        assertSame(source.response, new LoadWeatherPreviewForecast(source).execute(-8.3, -77.4));
        assertEquals(1, source.calls);
        assertEquals(-8.3, source.latitude, 0);
        assertEquals(-77.4, source.longitude, 0);
    }
}
