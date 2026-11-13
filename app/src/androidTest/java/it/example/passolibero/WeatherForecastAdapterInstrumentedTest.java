package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import it.example.passolibero.application.weather.WeatherForecastPort;
import it.example.passolibero.data.remote.OpenMeteoForecastAdapter;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** ADR-002/#32 G1: compare the legacy forecast values with the adapter, without network. */
@RunWith(AndroidJUnit4.class)
public final class WeatherForecastAdapterInstrumentedTest {
    private static void onDisposableAndroid11Emulator() {
        assumeTrue("API 30 only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Do not run integration fixtures on personal hardware",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    @Test
    public void keepsLegacyForecastOrderZoneAndHourValuesExactly() throws IOException {
        onDisposableAndroid11Emulator();
        WeatherApi.Hour first = new WeatherApi.Hour(200, 23.75, 12.5);
        WeatherApi.Hour second = new WeatherApi.Hour(100, -1.25, 2.0);
        WeatherApi.Forecast raw =
                new WeatherApi.Forecast(Arrays.asList(first, second), ZoneId.of("Europe/Rome"));
        double[] coordinates = {Double.NaN, Double.NaN};
        OpenMeteoForecastAdapter adapter =
                new OpenMeteoForecastAdapter(
                        (lat, lon) -> {
                            coordinates[0] = lat;
                            coordinates[1] = lon;
                            return raw;
                        });

        WeatherForecastPort.Forecast forecast = adapter.forecast(40.68, 14.77);

        assertEquals(40.68, coordinates[0], 0);
        assertEquals(14.77, coordinates[1], 0);
        assertEquals(raw.zone, forecast.zone);
        assertEquals(2, forecast.hours.size());
        assertEquals(first.epochSec, forecast.hours.get(0).epochSec);
        assertEquals(first.temperatureC, forecast.hours.get(0).temperatureC, 0);
        assertEquals(first.windKmh, forecast.hours.get(0).windKmh, 0);
        assertEquals(second.epochSec, forecast.hours.get(1).epochSec);
        assertEquals(second.temperatureC, forecast.hours.get(1).temperatureC, 0);
        assertEquals(second.windKmh, forecast.hours.get(1).windKmh, 0);
    }

    @Test
    public void propagatesOfflineExceptionAndDoesNotInventHours() throws IOException {
        onDisposableAndroid11Emulator();
        OpenMeteoForecastAdapter empty =
                new OpenMeteoForecastAdapter(
                        (lat, lon) ->
                                new WeatherApi.Forecast(
                                        Collections.emptyList(), ZoneId.of("UTC")));
        assertEquals(0, empty.forecast(3, 4).hours.size());

        IOException offline = new IOException("forecast unavailable");
        OpenMeteoForecastAdapter failing =
                new OpenMeteoForecastAdapter(
                        (lat, lon) -> {
                            throw offline;
                        });
        try {
            failing.forecast(40.68, 14.77);
            fail("Do not turn a network error into a successful preview");
        } catch (IOException caught) {
            assertSame(offline, caught);
        }
    }
}
