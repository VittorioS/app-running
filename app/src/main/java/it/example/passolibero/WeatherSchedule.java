package it.example.passolibero;

import it.example.passolibero.domain.weather.NextWeatherAlarmPolicy;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Orari locali HH:mm giornalieri, indipendenti da Android e dai fusi del servizio meteo. */
public final class WeatherSchedule {
    private WeatherSchedule() { }

    public static String canonical(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59)
            throw new IllegalArgumentException("Orario non valido");
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    /** Restituisce orari ordinati e distinti; eventuali dati corrotti sono ignorati. */
    public static List<String> decode(String encoded) {
        TreeSet<String> times = new TreeSet<>();
        if (encoded != null) for (String token : encoded.split(",")) {
            String time = token.trim();
            if (!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) continue;
            times.add(time);
        }
        return new ArrayList<>(times);
    }

    public static String encode(List<String> times) {
        TreeSet<String> ordered = new TreeSet<>();
        for (String time : times) {
            if (time == null || !time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))
                throw new IllegalArgumentException("Orario non valido: " + time);
            ordered.add(time);
        }
        return String.join(",", ordered);
    }

    /** Prossima occorrenza futura nell'ora locale del telefono, rispettando l'ora legale. */
    public static long nextEpochMillis(List<String> times, ZoneId zone, long nowMillis) {
        return NextWeatherAlarmPolicy.nextEpochMillis(times, zone, nowMillis);
    }
}
