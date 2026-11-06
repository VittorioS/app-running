import it.example.passolibero.WeatherRules;

/** Test senza Android né accesso a Internet. */
public final class WeatherRulesTest {
    private static void yes(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Regole non valide accettate");
    }
    public static void main(String[] args) {
        WeatherRules everything = new WeatherRules(null, null, null);
        yes(everything.matches(-5, 180), "Tutti i campi omessi = qualsiasi condizione");
        yes(!everything.matches(Double.NaN, 2), "Dati mancanti non generano alert");
        WeatherRules min = new WeatherRules(15.0, null, null);
        yes(min.matches(15, 80) && !min.matches(14.9, 0), "Temperatura minima inclusiva");
        WeatherRules max = new WeatherRules(null, 22.0, null);
        yes(max.matches(22, 0) && !max.matches(22.1, 0), "Temperatura massima inclusiva");
        WeatherRules wind = new WeatherRules(null, null, 12.0);
        yes(wind.matches(-20, 12) && !wind.matches(20, 12.1), "Vento massimo inclusivo");
        WeatherRules combined = new WeatherRules(15.0, 23.0, 16.0);
        yes(combined.matches(20, 10), "AND tutte soddisfatte");
        yes(!combined.matches(24, 10), "AND temperatura alta");
        yes(!combined.matches(20, 17), "AND vento alto");
        yes(!combined.matches(14, 10), "AND temperatura bassa");
        yes(new WeatherRules(null, null, 0.0).matches(20, 0), "Zero è valore ammesso");
        yes(WeatherRules.optional(" ") == null, "Vuoto = any");
        yes(WeatherRules.optional("12,5") == 12.5, "Virgola decimale italiana");
        invalid(() -> WeatherRules.optional("abc"));
        invalid(() -> new WeatherRules(25.0, 20.0, null));
        invalid(() -> new WeatherRules(null, null, -1.0));
        long now = 1_000_000;
        yes(WeatherRules.shouldNotify(now, now + 3600, 0, -1), "Finestra futura");
        yes(!WeatherRules.shouldNotify(now, now + 3600, now - 2000, -1), "Cooldown 6h");
        yes(!WeatherRules.shouldNotify(now, now + 3600, 0, now + 3600), "No duplicati");
        yes(!WeatherRules.shouldNotify(now, now - 4000, 0, -1), "Slot passato");
        yes(!WeatherRules.shouldNotify(now, now + 7 * 3600, 0, -1), "Non troppo in anticipo");
        System.out.println("PASS: condizioni meteo AND, campi facoltativi, orari e anti-spam");
    }
}
