import it.example.passolibero.WeatherSchedule;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

public final class WeatherScheduleTest {
    static void check(boolean okay, String msg) {
        if (!okay) throw new AssertionError(msg);
    }
    static long at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0,
                ZoneId.of("Europe/Rome")).toInstant().toEpochMilli();
    }
    public static void main(String[] args) {
        List<String> times = WeatherSchedule.decode("18:00,06:00,11:00,06:00,garbage");
        check(times.equals(Arrays.asList("06:00", "11:00", "18:00")), "Ordine e senza duplicati");
        check(WeatherSchedule.encode(times).equals("06:00,11:00,18:00"), "Persistenza degli orari");
        check(WeatherSchedule.decode("").isEmpty(), "Lista vuota valida");
        check(WeatherSchedule.nextEpochMillis(times, ZoneId.of("Europe/Rome"),
                at(2026, 11, 6, 5, 0)) == at(2026, 11, 6, 6, 0), "Prima mattina");
        check(WeatherSchedule.nextEpochMillis(times, ZoneId.of("Europe/Rome"),
                at(2026, 11, 6, 6, 0)) == at(2026, 11, 6, 11, 0), "Orario gia raggiunto");
        check(WeatherSchedule.nextEpochMillis(times, ZoneId.of("Europe/Rome"),
                at(2026, 11, 6, 11, 30)) == at(2026, 11, 6, 18, 0), "Prossima fascia");
        check(WeatherSchedule.nextEpochMillis(times, ZoneId.of("Europe/Rome"),
                at(2026, 11, 6, 19, 0)) == at(2026, 11, 7, 6, 0), "Giorno successivo");
        check(WeatherSchedule.nextEpochMillis(times, ZoneId.of("Europe/Rome"),
                at(2026, 11, 6, 19, 0)) > at(2026, 11, 6, 19, 0), "Non allarme immediato");
        check(WeatherSchedule.nextEpochMillis(WeatherSchedule.decode(""),
                ZoneId.of("Europe/Rome"), 0) == -1, "Nessun allarme senza orari");
        check(WeatherSchedule.canonical(0, 5).equals("00:05"), "TimePicker formattato");
        check(WeatherSchedule.canonical(23, 59).equals("23:59"), "Ultima ora valida");
        // Passaggio ora legale: 02:30 non esiste, Java la sposta avanti a 03:30.
        check(WeatherSchedule.nextEpochMillis(WeatherSchedule.decode("02:30"),
                ZoneId.of("Europe/Rome"), at(2027, 3, 28, 0, 30))
                == at(2027, 3, 28, 3, 30), "Ora legale senza loop");
        System.out.println("PASS: pianificazione orari giornalieri e cambio ora legale");
    }
}
