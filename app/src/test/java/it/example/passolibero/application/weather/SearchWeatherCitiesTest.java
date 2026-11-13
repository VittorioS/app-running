package it.example.passolibero.application.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** ADR-002/#32: characterize city search with an offline, deterministic HTTP fake. */
public final class SearchWeatherCitiesTest {
    private static final class FakeSearch implements WeatherCitySearchPort {
        int calls;
        String query;
        List<City> result = Collections.emptyList();
        IOException error;

        @Override
        public List<City> search(String requestedQuery) throws IOException {
            calls++;
            query = requestedQuery;
            if (error != null) throw error;
            return result;
        }
    }

    @Test
    public void preservesQueryResultOrderAndExactCityFields() throws IOException {
        FakeSearch source = new FakeSearch();
        WeatherCitySearchPort.City first =
                new WeatherCitySearchPort.City("Salerno · Campania, Italia", 40.68, 14.77);
        WeatherCitySearchPort.City second =
                new WeatherCitySearchPort.City("Salerno, Colombia", 4.17, -75.96);
        source.result = Arrays.asList(first, second);

        List<WeatherCitySearchPort.City> found = new SearchWeatherCities(source).execute("Salerno");

        assertSame("The use case must not reorder or filter results", source.result, found);
        assertSame(first, found.get(0));
        assertSame(second, found.get(1));
        assertEquals("Salerno", source.query);
        assertEquals(1, source.calls);
        assertEquals("Salerno · Campania, Italia", found.get(0).title);
        assertEquals(40.68, found.get(0).latitude, 0);
        assertEquals(14.77, found.get(0).longitude, 0);
    }

    @Test
    public void forwardsEmptyListWithoutInventingCities() throws IOException {
        FakeSearch source = new FakeSearch();
        List<WeatherCitySearchPort.City> found =
                new SearchWeatherCities(source).execute("nonexistent");
        assertSame(source.result, found);
        assertEquals(0, found.size());
        assertEquals(1, source.calls);
    }

    @Test
    public void propagatesOriginalIOExceptionToExistingUiErrorHandler() {
        FakeSearch source = new FakeSearch();
        IOException unavailable = new IOException("Open-Meteo unavailable");
        source.error = unavailable;

        try {
            new SearchWeatherCities(source).execute("Salerno");
            fail("The use case must not turn a network error into a blank list");
        } catch (IOException caught) {
            assertSame(unavailable, caught);
        }
        assertEquals(1, source.calls);
    }

    @Test
    public void neverMutatesOrSortsCallerResult() throws IOException {
        FakeSearch source = new FakeSearch();
        source.result = new ArrayList<>();
        source.result.add(new WeatherCitySearchPort.City("Zulu", -2, -2));
        source.result.add(new WeatherCitySearchPort.City("Alpha", 2, 2));

        List<WeatherCitySearchPort.City> actual = new SearchWeatherCities(source).execute("za");

        assertSame(source.result, actual);
        assertEquals("Zulu", actual.get(0).title);
        assertEquals("Alpha", actual.get(1).title);
        assertEquals(1, source.calls);
    }
}
