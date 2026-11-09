package it.example.passolibero;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * JUnit migration of tests/WeatherRulesTest.java (20 legacy assertions).
 *
 * <p>This remains intentionally behavior-equivalent, with unchanged input values,
 * boolean expressions and boundary cases. The original Java main suite runs
 * alongside it during the parity check before the legacy invocation is removed.
 */
public final class WeatherRulesTest {
    @Test public void conditionsAreCombinedAndBoundsInclusive() {
        WeatherRules everything = new WeatherRules(null, null, null);
        assertTrue("Tutti i campi omessi = qualsiasi condizione",
                everything.matches(-5, 180));
        assertTrue("Dati mancanti non generano alert",
                !everything.matches(Double.NaN, 2));
        WeatherRules min = new WeatherRules(15.0, null, null);
        assertTrue("Temperatura minima inclusiva",
                min.matches(15, 80) && !min.matches(14.9, 0));
        WeatherRules max = new WeatherRules(null, 22.0, null);
        assertTrue("Temperatura massima inclusiva",
                max.matches(22, 0) && !max.matches(22.1, 0));
        WeatherRules wind = new WeatherRules(null, null, 12.0);
        assertTrue("Vento massimo inclusivo",
                wind.matches(-20, 12) && !wind.matches(20, 12.1));
        WeatherRules combined = new WeatherRules(15.0, 23.0, 16.0);
        assertTrue("AND tutte soddisfatte", combined.matches(20, 10));
        assertTrue("AND temperatura alta", !combined.matches(24, 10));
        assertTrue("AND vento alto", !combined.matches(20, 17));
        assertTrue("AND temperatura bassa", !combined.matches(14, 10));
        assertTrue("Zero è valore ammesso",
                new WeatherRules(null, null, 0.0).matches(20, 0));
    }

    @Test public void optionalThresholdsAndInvalidConfiguration() {
        assertTrue("Vuoto = any", WeatherRules.optional(" ") == null);
        assertTrue("Virgola decimale italiana",
                WeatherRules.optional("12,5") == 12.5);
        assertThrows("Regole non valide accettate",
                IllegalArgumentException.class, () -> WeatherRules.optional("abc"));
        assertThrows("Regole non valide accettate",
                IllegalArgumentException.class, () -> new WeatherRules(25.0, 20.0, null));
        assertThrows("Regole non valide accettate",
                IllegalArgumentException.class, () -> new WeatherRules(null, null, -1.0));
    }

    @Test public void notificationTimeWindowCooldownAndDeduplication() {
        long now = 1_000_000;
        assertTrue("Finestra futura",
                WeatherRules.shouldNotify(now, now + 3600, 0, -1));
        assertTrue("Cooldown 6h",
                !WeatherRules.shouldNotify(now, now + 3600, now - 2000, -1));
        assertTrue("No duplicati",
                !WeatherRules.shouldNotify(now, now + 3600, 0, now + 3600));
        assertTrue("Slot passato",
                !WeatherRules.shouldNotify(now, now - 4000, 0, -1));
        assertTrue("Non troppo in anticipo",
                !WeatherRules.shouldNotify(now, now + 7 * 3600, 0, -1));
    }
}
