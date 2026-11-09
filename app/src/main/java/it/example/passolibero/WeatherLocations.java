package it.example.passolibero;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Lista di localita' meteo: serializzazione e deduplicazione senza dipendenze Android. */
public final class WeatherLocations {
    public static final int MAX_CITIES = 20;

    public static final class City {
        public final String title;
        public final double latitude;
        public final double longitude;

        public City(String title, double latitude, double longitude) {
            if (title == null || title.trim().isEmpty() || title.length() > 200
                    || !Double.isFinite(latitude) || Math.abs(latitude) > 90
                    || !Double.isFinite(longitude) || Math.abs(longitude) > 180) {
                throw new IllegalArgumentException("Localita' meteo non valida");
            }
            this.title = title.trim();
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private WeatherLocations() { }

    public static City legacy(String title, double latitude, double longitude) {
        try {
            return new City(title, latitude, longitude);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean samePlace(City a, City b) {
        return a != null && b != null
                && Double.doubleToLongBits(a.latitude) == Double.doubleToLongBits(b.latitude)
                && Double.doubleToLongBits(a.longitude) == Double.doubleToLongBits(b.longitude);
    }

    public static boolean contains(List<City> cities, City city) {
        if (cities == null || city == null) return false;
        for (City existing : cities) if (samePlace(existing, city)) return true;
        return false;
    }

    /** False per duplicati o limite raggiunto: non cancella mai localita' esistenti. */
    public static boolean add(List<City> cities, City city) {
        if (cities == null || city == null || contains(cities, city)
                || cities.size() >= MAX_CITIES) return false;
        cities.add(city);
        return true;
    }

    /** Chiave persistente per cooldown per citta'; dipende dalle coordinate, non dal nome. */
    public static String key(City city) {
        return Long.toHexString(Double.doubleToLongBits(city.latitude)) + "_"
                + Long.toHexString(Double.doubleToLongBits(city.longitude));
    }

    /**
     * Formato per riga: titolo UTF-8 base64url|latitudine|longitudine.
     * I titoli possono contenere virgole, accenti e caratteri speciali.
     */
    public static String encode(List<City> cities) {
        List<City> unique = new ArrayList<>();
        if (cities != null) for (City city : cities) {
            if (city == null) throw new IllegalArgumentException("Localita' nulla");
            if (!add(unique, city) && !contains(unique, city))
                throw new IllegalArgumentException("Troppe localita' meteo");
        }
        StringBuilder result = new StringBuilder();
        for (City city : unique) {
            if (result.length() > 0) result.append('\n');
            result.append(Base64.getUrlEncoder().withoutPadding().encodeToString(
                    city.title.getBytes(StandardCharsets.UTF_8)));
            result.append('|').append(Double.toString(city.latitude));
            result.append('|').append(Double.toString(city.longitude));
        }
        return result.toString();
    }

    /** Salta righe corrotte senza far crashare l'app. Nessun fallback se la lista e' vuota. */
    public static List<City> decode(String text) {
        List<City> cities = new ArrayList<>();
        if (text == null || text.isEmpty()) return cities;
        for (String record : text.split("\n")) {
            String[] parts = record.split("\\|", -1);
            if (parts.length != 3) continue;
            try {
                String name = new String(Base64.getUrlDecoder().decode(parts[0]),
                        StandardCharsets.UTF_8);
                City city = new City(name, Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]));
                add(cities, city);
            } catch (IllegalArgumentException ignored) {
                // Riga incompleta, coordinate invalide, base64 corrotto.
            }
        }
        return cities;
    }
}
