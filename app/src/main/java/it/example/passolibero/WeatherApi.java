package it.example.passolibero;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Client HTTPS leggero per Open-Meteo. Nessuna libreria o chiave API. */
public final class WeatherApi {
    private WeatherApi() { }

    public static final class City {
        public final String title;
        public final double latitude, longitude;
        City(String title, double latitude, double longitude) {
            this.title = title;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public static final class Hour {
        public final long epochSec;
        public final double temperatureC, windKmh;
        Hour(long epochSec, double temperatureC, double windKmh) {
            this.epochSec = epochSec;
            this.temperatureC = temperatureC;
            this.windKmh = windKmh;
        }
    }

    public static final class Forecast {
        public final List<Hour> hours;
        public final ZoneId zone;
        Forecast(List<Hour> hours, ZoneId zone) {
            this.hours = hours;
            this.zone = zone;
        }
    }

    // API 30+: usare overload con String: Charset overload e' disponibile solo da API 33.
    public static List<City> findCities(String query) throws IOException {
        if (query == null || query.trim().length() < 2)
            throw new IOException("Inserisci almeno due caratteri per cercare la città");
        String encoded = URLEncoder.encode(query.trim(), "UTF-8");
        JSONObject json = read("https://geocoding-api.open-meteo.com/v1/search?name="
                + encoded + "&count=6&language=it&format=json");
        JSONArray results = json.optJSONArray("results");
        List<City> cities = new ArrayList<>();
        if (results == null) return cities;
        for (int i = 0; i < results.length(); i++) {
            JSONObject city = results.optJSONObject(i);
            if (city == null) continue;
            double lat = city.optDouble("latitude", Double.NaN);
            double lon = city.optDouble("longitude", Double.NaN);
            if (!Double.isFinite(lat) || !Double.isFinite(lon)) continue;
            String name = city.optString("name", "");
            String admin = city.optString("admin1", "");
            String country = city.optString("country", "");
            String title = name + (admin.isEmpty() ? "" : " · " + admin)
                    + (country.isEmpty() ? "" : ", " + country);
            cities.add(new City(title, lat, lon));
        }
        return cities;
    }

    public static Forecast forecast(double latitude, double longitude) throws IOException {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)
            throw new IOException("Coordinate della città non valide");
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f"
              + "&hourly=temperature_2m,wind_speed_10m"
              + "&temperature_unit=celsius&wind_speed_unit=kmh"
              + "&timeformat=unixtime&timezone=auto&forecast_days=2",
                latitude, longitude);
        JSONObject data = read(url);
        JSONObject hourly = data.optJSONObject("hourly");
        if (hourly == null) throw new IOException("Previsioni orarie non disponibili");
        JSONArray times = hourly.optJSONArray("time");
        JSONArray temperatures = hourly.optJSONArray("temperature_2m");
        JSONArray winds = hourly.optJSONArray("wind_speed_10m");
        if (times == null || temperatures == null || winds == null)
            throw new IOException("Dati temperatura/vento assenti");
        List<Hour> hours = new ArrayList<>();
        int count = Math.min(times.length(), Math.min(temperatures.length(), winds.length()));
        for (int i = 0; i < count; i++) {
            long time = times.optLong(i, -1);
            double temp = temperatures.optDouble(i, Double.NaN);
            double wind = winds.optDouble(i, Double.NaN);
            if (time <= 0 || !Double.isFinite(temp) || !Double.isFinite(wind)) continue;
            hours.add(new Hour(time, temp, wind));
        }
        if (hours.isEmpty()) throw new IOException("Nessuna previsione oraria utilizzabile");
        ZoneId zone;
        try { zone = ZoneId.of(data.optString("timezone", "UTC")); }
        catch (RuntimeException ex) { zone = ZoneId.of("UTC"); }
        return new Forecast(hours, zone);
    }

    private static JSONObject read(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(12_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "PassoLibero/2 (Android; weather preferences)");
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("Servizio Open-Meteo HTTP " + code);
            try (InputStream stream = connection.getInputStream()) {
                byte[] bytes = new byte[4096];
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                int n;
                while ((n = stream.read(bytes)) != -1) {
                    if (buffer.size() + n > 512_000)
                        throw new IOException("Risposta meteo troppo grande");
                    buffer.write(bytes, 0, n);
                }
                try {
                    return new JSONObject(buffer.toString("UTF-8"));
                } catch (JSONException ex) {
                    throw new IOException("Risposta meteo non valida", ex);
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
