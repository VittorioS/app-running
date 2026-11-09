package it.example.passolibero;

/** Criteri meteo AND. Una soglia nulla non limita il valore corrispondente. */
public final class WeatherRules {
    public final Double minTemperatureC;
    public final Double maxTemperatureC;
    public final Double maxWindKmh;

    public WeatherRules(Double minTemperatureC, Double maxTemperatureC, Double maxWindKmh) {
        if (minTemperatureC != null && (!Double.isFinite(minTemperatureC)
                || minTemperatureC < -90 || minTemperatureC > 65)) {
            throw new IllegalArgumentException("Temperatura minima fuori intervallo (-90 a 65 °C)");
        }
        if (maxTemperatureC != null && (!Double.isFinite(maxTemperatureC)
                || maxTemperatureC < -90 || maxTemperatureC > 65)) {
            throw new IllegalArgumentException("Temperatura massima fuori intervallo (-90 a 65 °C)");
        }
        if (minTemperatureC != null && maxTemperatureC != null
                && minTemperatureC > maxTemperatureC) {
            throw new IllegalArgumentException("La temperatura minima supera la massima");
        }
        if (maxWindKmh != null && (!Double.isFinite(maxWindKmh)
                || maxWindKmh < 0 || maxWindKmh > 300)) {
            throw new IllegalArgumentException("Il vento massimo deve essere tra 0 e 300 km/h");
        }
        this.minTemperatureC = minTemperatureC;
        this.maxTemperatureC = maxTemperatureC;
        this.maxWindKmh = maxWindKmh;
    }

    /** Campo vuoto = nessun vincolo. Anche zero è una soglia valida. */
    public static Double optional(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        try {
            double v = Double.parseDouble(input.trim().replace(',', '.'));
            if (!Double.isFinite(v)) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Inserisci un numero valido oppure lascia vuoto");
        }
    }

    public boolean matches(double temperatureC, double windKmh) {
        // Dati meteo assenti non devono generare falsi avvisi, nemmeno senza filtri.
        if (!Double.isFinite(temperatureC) || !Double.isFinite(windKmh)) return false;
        return (minTemperatureC == null || temperatureC >= minTemperatureC)
                && (maxTemperatureC == null || temperatureC <= maxTemperatureC)
                && (maxWindKmh == null || windKmh <= maxWindKmh);
    }

    public static boolean shouldNotify(long nowEpochSec, long slotEpochSec,
                                       long lastNotificationEpochSec, long lastNotifiedSlotSec) {
        // Un'ora rappresenta la previsione nell'intervallo [slot, slot + 1h).
        if (slotEpochSec + 3600 <= nowEpochSec || slotEpochSec > nowEpochSec + 6 * 3600) {
            return false;
        }
        if (slotEpochSec == lastNotifiedSlotSec) return false;
        // Limita il numero di avvisi anche se molte ore consecutive soddisfano i criteri.
        return lastNotificationEpochSec <= 0
                || nowEpochSec < lastNotificationEpochSec
                || nowEpochSec - lastNotificationEpochSec >= 6 * 3600;
    }
}
