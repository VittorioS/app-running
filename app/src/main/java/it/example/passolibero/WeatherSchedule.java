package it.example.passolibero;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
        if (times == null || times.isEmpty()) return -1;
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        long best = Long.MAX_VALUE;
        for (int days = 0; days <= 2; days++) {
            LocalDate date = today.plusDays(days);
            for (String hhmm : times) {
                LocalTime time = LocalTime.parse(hhmm);
                long candidate = date.atTime(time).atZone(zone).toInstant().toEpochMilli();
                // Salta il controllo se l'ora è già passata; scegli domani.
                if (candidate > nowMillis && candidate < best) best = candidate;
            }
        }
        return best == Long.MAX_VALUE ? -1 : best;
    }
}
