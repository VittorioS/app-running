package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import it.example.passolibero.application.weather.WeatherCitySearchPort;
import it.example.passolibero.data.remote.OpenMeteoCitySearchAdapter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** ADR-002/#32: map legacy Open-Meteo city results without making HTTP requests. */
@RunWith(AndroidJUnit4.class)
public final class WeatherCitySearchAdapterInstrumentedTest {
    private static void onAndroid11EmulatorOnly() {
        assumeTrue("Android 11 test", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Do not run network integration tests on real devices",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    @Test
    public void preservesCityNamesCoordinatesOrderAndLimitFromLegacyResult()
            throws IOException {
        onAndroid11EmulatorOnly();
        WeatherApi.City first = new WeatherApi.City("S. Pietro · Lazio, Italia", 42.1, 12.6);
        WeatherApi.City second = new WeatherApi.City("Salerno, Colombia", 4.17, -75.96);
        String[] receivedQuery = {null};

        OpenMeteoCitySearchAdapter adapter =
                new OpenMeteoCitySearchAdapter(
                        query -> {
                            receivedQuery[0] = query;
                            return Arrays.asList(first, second);
                        });
        List<WeatherCitySearchPort.City> result = adapter.search("Salerno");

        assertEquals("Salerno", receivedQuery[0]);
        assertEquals(2, result.size());
        assertEquals(first.title, result.get(0).title);
        assertEquals(first.latitude, result.get(0).latitude, 0);
        assertEquals(first.longitude, result.get(0).longitude, 0);
        assertEquals(second.title, result.get(1).title);
        assertEquals(second.latitude, result.get(1).latitude, 0);
        assertEquals(second.longitude, result.get(1).longitude, 0);
    }

    @Test
    public void emptyResultsAndIoErrorsStayVisibleWithoutNetwork() throws IOException {
        onAndroid11EmulatorOnly();
        OpenMeteoCitySearchAdapter empty =
                new OpenMeteoCitySearchAdapter(query -> Collections.emptyList());
        assertEquals(0, empty.search("unknown").size());

        IOException offline = new IOException("offline");
        OpenMeteoCitySearchAdapter broken =
                new OpenMeteoCitySearchAdapter(
                        query -> {
                            throw offline;
                        });
        try {
            broken.search("Salerno");
            fail("The real UI needs the original IOException for its error path");
        } catch (IOException caught) {
            assertSame(offline, caught);
        }
    }
}
