import it.example.passolibero.WeatherLocations;
import java.util.ArrayList;
import java.util.List;

public final class WeatherLocationsTest {
    private static void check(boolean yes, String label) {
        if (!yes) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        List<WeatherLocations.City> cities = new ArrayList<>();
        WeatherLocations.City salerno = new WeatherLocations.City(
                "Salerno · Campania, Italia", 40.6824, 14.7681);
        WeatherLocations.City napoli = new WeatherLocations.City(
                "Napoli | Campania, Italia", 40.8518, 14.2681);
        check(WeatherLocations.add(cities, salerno), "Aggiunge Salerno");
        check(WeatherLocations.add(cities, napoli), "Aggiunge Napoli");
        check(!WeatherLocations.add(cities, salerno), "Non duplica Salerno");
        check(cities.size() == 2, "Numero esatto");
        String encoded = WeatherLocations.encode(cities);
        List<WeatherLocations.City> decoded = WeatherLocations.decode(encoded);
        check(decoded.size() == 2, "Roundtrip lista");
        check(decoded.get(0).title.equals(salerno.title), "Accenti e virgole");
        check(decoded.get(1).title.equals(napoli.title), "Titolo con separatore");
        check(WeatherLocations.samePlace(decoded.get(0), salerno), "Coordinate integre");
        check(WeatherLocations.contains(decoded, napoli), "Riconosce luogo");
        check(!WeatherLocations.key(salerno).equals(WeatherLocations.key(napoli)),
                "Notifiche indipendenti per citta'");
        check(WeatherLocations.key(decoded.get(0)).equals(WeatherLocations.key(salerno)),
                "Identificatore stabile dopo riavvio");
        check(WeatherLocations.decode("").isEmpty(), "Lista vuota resta vuota");
        check(WeatherLocations.decode("corrotto\n" + encoded + "\naltro").size() == 2,
                "Ignora righe corrotte");
        check(WeatherLocations.legacy("Salerno", 40.68, 14.76) != null,
                "Migrazione localita' singola");
        check(WeatherLocations.legacy("Salerno", Double.NaN, 14.76) == null,
                "Migrazione invalida scartata");
        for (int i = 0; i < WeatherLocations.MAX_CITIES - 2; i++)
            check(WeatherLocations.add(cities, new WeatherLocations.City("Citta " + i,
                    i, 10)), "Aggiunge entro limite");
        check(!WeatherLocations.add(cities, new WeatherLocations.City("Eccesso",
                80, 20)), "Limite di sicurezza");
        check(WeatherLocations.decode(WeatherLocations.encode(cities)).size()
                == WeatherLocations.MAX_CITIES, "Persistenza al limite");
        System.out.println("PASS: lista localita' meteo, migrazione, duplicati e identita' alert");
    }
}
