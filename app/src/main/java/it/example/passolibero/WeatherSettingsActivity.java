package it.example.passolibero;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import it.example.passolibero.application.weather.WeatherCitySearchPort;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Preferenze meteo per la corsa; città scelta manualmente, nessun GPS in background. */
public final class WeatherSettingsActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 452;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private EditText citySearch, minTemp, maxTemp, maxWind;
    private TextView selectedLabel, resultsView, scheduleSummary, searchStatus;
    private LinearLayout scheduleRows, cityResults, savedCitiesRows;
    private final List<String> times = new ArrayList<>();
    private final List<WeatherLocations.City> cities = new ArrayList<>();
    private int searchSerial;
    private volatile int previewSerial;
    private Switch enabled;
    private Button searchButton, forecastButton, saveButton;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = AppPrefs.get(this);
        times.addAll(WeatherSchedule.decode(prefs.getString("weather_times", "")));
        cities.addAll(WeatherMonitor.cities(prefs));
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        setContentView(root);
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll);
        LinearLayout content = Ui.vertical(this);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 18),
                Ui.dp(this, 18), Ui.dp(this, 28));
        scroll.addView(content);

        Ui.marginBottom(this, content,
                Ui.text(this, "Meteo per correre", 26, true, Ui.INK), 6);
        Ui.marginBottom(this, content, Ui.text(this,
                "Notifiche locali quando la previsione oraria soddisfa tutte le tue condizioni.",
                14, false, Ui.MUTED), 14);

        LinearLayout card = Ui.card(this);
        enabled = new Switch(this);
        enabled.setText("Attiva notifiche meteo favorevole");
        enabled.setChecked(prefs.getBoolean("weather_enabled", false));
        Ui.marginBottom(this, card, enabled, 12);
        Ui.marginBottom(this, card,
                Ui.text(this, "CITTÀ DA MONITORARE", 13, true, Ui.MUTED), 6);
        Ui.marginBottom(this, card, Ui.text(this,
                "Cerca una città e toccala nei risultati per aggiungerla alla lista. "
              + "Puoi monitorare fino a 20 città con gli stessi criteri e orari.",
                13, false, Ui.MUTED), 8);
        citySearch = field("Cerca città (es. Salerno)", "", false);
        citySearch.setSingleLine(true);
        // Campo di ricerca separato dalla localita' confermata e salvata.
        citySearch.setText("");
        Ui.marginBottom(this, card, citySearch, 5);

        searchButton = Ui.button(this, "Cerca località", false);
        searchButton.setOnClickListener(v -> searchCities());
        Ui.marginBottom(this, card, searchButton, 9);

        selectedLabel = Ui.text(this, "", 14, true, Ui.GREEN);
        Ui.marginBottom(this, card, selectedLabel, 8);
        savedCitiesRows = Ui.vertical(this);
        Ui.marginBottom(this, card, savedCitiesRows, 8);
        renderCities();
        searchStatus = Ui.text(this,
                "Dopo la ricerca, tocca una località nell'elenco dei risultati.",
                13, false, Ui.MUTED);
        Ui.marginBottom(this, card, searchStatus, 8);
        cityResults = Ui.vertical(this);
        cityResults.setVisibility(View.GONE);
        Ui.marginBottom(this, card, cityResults, 14);

        Ui.marginBottom(this, card,
                Ui.text(this, "CONDIZIONI (TUTTE IN AND)", 13, true, Ui.MUTED), 7);
        Ui.marginBottom(this, card, Ui.text(this,
                "Lascia vuoto un campo per accettare qualsiasi valore.", 12, false, Ui.MUTED), 8);
        minTemp = field("Temperatura minima °C (facoltativa)",
                prefs.getString("weather_min_c", ""), true);
        Ui.marginBottom(this, card, minTemp, 7);
        maxTemp = field("Temperatura massima °C (facoltativa)",
                prefs.getString("weather_max_c", ""), true);
        Ui.marginBottom(this, card, maxTemp, 7);
        maxWind = field("Vento massimo km/h (facoltativo)",
                prefs.getString("weather_max_wind", ""), true);
        Ui.marginBottom(this, card, maxWind, 14);

        Ui.marginBottom(this, card, Ui.text(this, "ORARI DI CONTROLLO OGNI GIORNO",
                13, true, Ui.MUTED), 7);
        Ui.marginBottom(this, card, Ui.text(this,
                "Aggiungi tutti gli orari che vuoi. Tocca un orario per modificarlo o premi Rimuovi.",
                12, false, Ui.MUTED), 6);
        scheduleRows = Ui.vertical(this);
        Ui.marginBottom(this, card, scheduleRows, 8);
        Button addTime = Ui.button(this, "＋ Aggiungi orario", false);
        addTime.setOnClickListener(v -> chooseTime(null));
        Ui.marginBottom(this, card, addTime, 8);
        scheduleSummary = Ui.text(this, "", 12, false, Ui.MUTED);
        Ui.marginBottom(this, card, scheduleSummary, 12);
        renderTimes();

        saveButton = Ui.button(this, "Salva condizioni e notifiche", true);
        saveButton.setOnClickListener(v -> save());
        Ui.marginBottom(this, card, saveButton, 10);

        forecastButton = Ui.button(this, "↻ Aggiorna condizioni meteo adesso", false);
        forecastButton.setOnClickListener(v -> previewForecast());
        Ui.marginBottom(this, card, forecastButton, 8);
        Ui.marginBottom(this, content, card, 12);

        LinearLayout forecastCard = Ui.card(this);
        Ui.marginBottom(this, forecastCard,
                Ui.text(this, "PREVISIONI ORARIE · OPEN-METEO", 14, true, Ui.INK), 8);
        resultsView = Ui.text(this, "Aggiungi una o più città e tocca «Aggiorna condizioni meteo adesso».",
                13, false, Ui.MUTED);
        forecastCard.addView(resultsView);
        Ui.marginBottom(this, content, forecastCard, 12);

        Ui.marginBottom(this, content, Ui.text(this,
                "Le temperature e il vento sono STIME del modello meteo, non misure "
              + "del telefono. Gli orari sono quelli locali del telefono. Android "
              + "può ritardare i controlli programmati per il risparmio energetico, "
              + "quindi non è garantita l'ora esatta; è necessaria una connessione. "
              + "Con lista vuota non avvengono controlli automatici. "
              + "Massimo un avviso ogni 6 ore per ciascuna città. "
              + "Nessun GPS attivo e nessun servizio permanente.",
                13, false, Ui.MUTED), 12);
        Ui.marginBottom(this, content, Ui.text(this,
                "Dati meteo: Open-Meteo.com (previsioni, attribuzione al provider). "
              + "Servizio esterno: i nomi cercati e le coordinate delle città monitorate "
              + "vengono usati per le richieste.",
                12, false, Ui.MUTED), 14);
        Button back = Ui.button(this, "← Torna alla Home", false);
        back.setOnClickListener(v -> finish());
        Ui.marginBottom(this, content, back, 8);
    }

    private EditText field(String hint, String value, boolean numeric) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setText(value);
        if (numeric) edit.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return edit;
    }

    private WeatherRules readRules() {
        return new WeatherRules(WeatherRules.optional(minTemp.getText().toString()),
                WeatherRules.optional(maxTemp.getText().toString()),
                WeatherRules.optional(maxWind.getText().toString()));
    }

    private void save() {
        try {
            WeatherRules rules = readRules();
            if (enabled.isChecked() && cities.isEmpty()) {
                Toast.makeText(this, "Aggiungi almeno una città da monitorare", Toast.LENGTH_LONG).show();
                return;
            }
            if (enabled.isChecked() && times.isEmpty()) {
                Toast.makeText(this, "Aggiungi almeno un orario per attivare i controlli automatici",
                        Toast.LENGTH_LONG).show();
                return;
            }
            SharedPreferences.Editor change = prefs.edit()
                    .putString("weather_cities", WeatherLocations.encode(cities))
                    .putString("weather_times", WeatherSchedule.encode(times))
                    .putBoolean("weather_enabled", enabled.isChecked())
                    .putString("weather_min_c", rules.minTemperatureC == null ? "" : rules.minTemperatureC.toString())
                    .putString("weather_max_c", rules.maxTemperatureC == null ? "" : rules.maxTemperatureC.toString())
                    .putString("weather_max_wind", rules.maxWindKmh == null ? "" : rules.maxWindKmh.toString());
            change.apply();
            WeatherMonitor.updateSchedule(this);
            Toast.makeText(this, enabled.isChecked()
                    ? "Criteri salvati per " + cities.size() + " città: controlli attivi"
                    : "Criteri e lista città salvati: notifiche disattivate", Toast.LENGTH_LONG).show();
            if (enabled.isChecked() && Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATIONS);
            }
        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void invalidatePreview() {
        previewSerial++;
        if (forecastButton != null) forecastButton.setEnabled(true);
        if (resultsView != null) resultsView.setText(
                "Elenco delle città modificato. Premi «Aggiorna condizioni meteo adesso» "
              + "per consultare la lista aggiornata.");
    }

    private void renderCities() {
        savedCitiesRows.removeAllViews();
        selectedLabel.setText(cities.isEmpty()
                ? "Nessuna città nell'elenco."
                : cities.size() + (cities.size() == 1 ? " città da monitorare:" : " città da monitorare:"));
        for (WeatherLocations.City city : new ArrayList<>(cities)) {
            LinearLayout row = Ui.horizontal(this);
            TextView name = Ui.text(this, "📍 " + city.title, 13, false, Ui.INK);
            LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            label.rightMargin = Ui.dp(this, 6);
            row.addView(name, label);
            Button remove = Ui.button(this, "Rimuovi", false);
            remove.setContentDescription("Rimuovi " + city.title);
            remove.setOnClickListener(v -> {
                cities.remove(city);
                invalidatePreview();
                renderCities();
            });
            row.addView(remove, new LinearLayout.LayoutParams(
                    Ui.dp(this, 94), Ui.dp(this, 48)));
            Ui.marginBottom(this, savedCitiesRows, row, 8);
        }
        if (!cities.isEmpty()) {
            Ui.marginBottom(this, savedCitiesRows, Ui.text(this,
                    "Premi Salva per applicare aggiunte o rimozioni ai controlli automatici.",
                    12, false, Ui.MUTED), 4);
        }
    }

    private void chooseTime(String previous) {
        int hour = 6, minute = 0;
        if (previous != null) {
            String[] parts = previous.split(":");
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        }
        new TimePickerDialog(this, (picker, selectedHour, selectedMinute) -> {
            String value = WeatherSchedule.canonical(selectedHour, selectedMinute);
            if (previous != null) times.remove(previous);
            if (!times.contains(value)) times.add(value);
            java.util.Collections.sort(times);
            renderTimes();
        }, hour, minute, true).show();
    }

    private void renderTimes() {
        scheduleRows.removeAllViews();
        if (times.isEmpty()) {
            Ui.marginBottom(this, scheduleRows, Ui.text(this,
                    "Nessun controllo automatico programmato.", 13, false, Ui.MUTED), 4);
        }
        for (String time : new ArrayList<>(times)) {
            LinearLayout row = Ui.horizontal(this);
            Button change = Ui.button(this, "🕒 " + time + " · Modifica", false);
            change.setOnClickListener(v -> chooseTime(time));
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
            left.rightMargin = Ui.dp(this, 6);
            row.addView(change, left);
            Button remove = Ui.button(this, "Rimuovi", false);
            remove.setOnClickListener(v -> {
                times.remove(time);
                renderTimes();
            });
            row.addView(remove, new LinearLayout.LayoutParams(
                    Ui.dp(this, 96), Ui.dp(this, 48)));
            Ui.marginBottom(this, scheduleRows, row, 6);
        }
        scheduleSummary.setText(times.isEmpty()
                ? "Il controllo manuale resta disponibile anche senza orari."
                : times.size() + " controlli previsti al giorno · "
                        + String.join(", ", times) + ". Premi Salva per applicare.");
    }

    private void searchCities() {
        final String query = citySearch.getText().toString().trim();
        if (query.length() < 2) {
            searchStatus.setText("Inserisci almeno due caratteri per cercare una città.");
            return;
        }
        final int requestId = ++searchSerial;
        searchButton.setEnabled(false);
        searchStatus.setText("Ricerca di «" + query + "» in corso…");
        cityResults.removeAllViews();
        cityResults.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                final List<WeatherCitySearchPort.City> found = WeatherCitySearchBridge.search(query);
                main.post(() -> {
                    if (isFinishing() || isDestroyed() || requestId != searchSerial) return;
                    searchButton.setEnabled(true);
                    cityResults.removeAllViews();

                    if (found.isEmpty()) {
                        searchStatus.setText("Nessuna località trovata per «" + query
                                + "». Prova a inserire un nome diverso.");
                        cityResults.setVisibility(View.GONE);
                        return;
                    }
                    searchStatus.setText(found.size()
                            + (found.size() == 1 ? " località trovata. " : " località trovate. ")
                            + "Tocca il risultato corretto (città, regione e Paese):");
                    for (WeatherCitySearchPort.City city : found) {
                        Button option = Ui.button(this, "📍 " + city.title, false);
                        option.setTextSize(14);
                        option.setContentDescription("Aggiungi località " + city.title);
                        option.setOnClickListener(v -> {
                            WeatherLocations.City added = new WeatherLocations.City(
                                    city.title, city.latitude, city.longitude);
                            if (WeatherLocations.contains(cities, added)) {
                                searchStatus.setText("«" + city.title
                                        + "» è già nell'elenco delle città.");
                                return;
                            }
                            if (!WeatherLocations.add(cities, added)) {
                                searchStatus.setText("Puoi monitorare al massimo "
                                        + WeatherLocations.MAX_CITIES + " città.");
                                return;
                            }
                            invalidatePreview();
                            renderCities();
                            searchStatus.setText("✓ «" + city.title + "» aggiunta. "
                                    + "Puoi cercare altre città o premere Salva.");
                            cityResults.removeAllViews();
                            cityResults.setVisibility(View.GONE);
                            citySearch.setText("");
                        });
                        Ui.marginBottom(this, cityResults, option, 7);
                    }
                    cityResults.setVisibility(View.VISIBLE);
                });
            } catch (Exception error) {
                android.util.Log.w("PassoLiberoWeather", "Ricerca città fallita", error);
                main.post(() -> {
                    if (isFinishing() || isDestroyed() || requestId != searchSerial) return;
                    searchButton.setEnabled(true);
                    cityResults.removeAllViews();
                    cityResults.setVisibility(View.GONE);
                    searchStatus.setText("Ricerca non riuscita. Controlla la connessione e riprova. "
                            + "(" + error.getClass().getSimpleName() + ")");
                });
            }
        }, "WeatherCitySearch").start();
    }

    private void previewForecast() {
        if (cities.isEmpty()) {
            Toast.makeText(this, "Aggiungi prima una o più città", Toast.LENGTH_LONG).show();
            return;
        }
        final WeatherRules rules;
        try { rules = readRules(); }
        catch (IllegalArgumentException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        final List<WeatherLocations.City> requested = new ArrayList<>(cities);
        final int requestId = ++previewSerial;
        forecastButton.setEnabled(false);
        resultsView.setText("Caricamento delle previsioni per "
                + requested.size() + " città…");

        new Thread(() -> {
            StringBuilder report = new StringBuilder();
            int failures = 0;
            for (WeatherLocations.City city : requested) {
                if (requestId != previewSerial || isFinishing() || isDestroyed()) return;
                try {
                    WeatherApi.Forecast forecast = WeatherApi.forecast(city.latitude, city.longitude);
                    report.append(formatForecastCity(city, forecast, rules)).append("\n\n");
                } catch (Exception ex) {
                    failures++;
                    report.append("⚠ ").append(city.title)
                            .append(": previsioni non disponibili. Controlla la connessione.")
                            .append("\n\n");
                    android.util.Log.w("PassoLiberoWeather", "Previsioni non disponibili per "
                            + city.title, ex);
                }
                final String text = report.toString();
                main.post(() -> {
                    if (requestId != previewSerial || isFinishing() || isDestroyed()) return;
                    resultsView.setText(text + "\nCaricamento delle altre città in corso…");
                });
            }
            final int errors = failures;
            final String result = report.toString();
            main.post(() -> {
                if (requestId != previewSerial || isFinishing() || isDestroyed()) return;
                forecastButton.setEnabled(true);
                resultsView.setText(result + (errors > 0
                        ? errors + " città senza dati. Puoi riprovare più tardi."
                        : "Tutte le città controllate. Le previsioni possono variare."));
            });
        }, "WeatherMultiCityPreview").start();
    }

    private String formatForecastCity(WeatherLocations.City city,
                                      WeatherApi.Forecast forecast, WeatherRules rules) {
        long now = System.currentTimeMillis() / 1000L;
        DateTimeFormatter clock = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ITALY)
                .withZone(forecast.zone);
        StringBuilder text = new StringBuilder("📍 ").append(city.title)
                .append("\n✅ = criteri rispettati | ✕ = non rispettati\n");
        WeatherApi.Hour closest = null;
        int count = 0, matching = 0;
        for (WeatherApi.Hour hour : forecast.hours) {
            if (hour.epochSec <= now && hour.epochSec + 3600 > now) {
                closest = hour;
                break;
            }
        }
        if (closest != null) {
            text.append(rules.matches(closest.temperatureC, closest.windKmh)
                    ? "✅ Adesso favorevole" : "✕ Adesso non favorevole")
                    .append(" (stima oraria) · ")
                    .append(String.format(Locale.ITALY, "%.1f °C", closest.temperatureC))
                    .append(" · vento ")
                    .append(String.format(Locale.ITALY, "%.0f km/h", closest.windKmh))
                    .append("\n");
        }
        for (WeatherApi.Hour hour : forecast.hours) {
            if (hour.epochSec + 3600 <= now || hour.epochSec >= now + 24 * 3600) continue;
            boolean good = rules.matches(hour.temperatureC, hour.windKmh);
            count++;
            if (good) matching++;
            text.append(good ? "✅ " : "✕ ")
                    .append(clock.format(Instant.ofEpochSecond(hour.epochSec)))
                    .append(" · ").append(String.format(Locale.ITALY, "%.1f °C", hour.temperatureC))
                    .append(" · vento ")
                    .append(String.format(Locale.ITALY, "%.0f km/h", hour.windKmh))
                    .append('\n');
        }
        return text.append(matching).append(" ore favorevoli su ")
                .append(count).append(" nelle prossime 24 ore.").toString();
    }

    @Override public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_NOTIFICATIONS
                && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "Le notifiche sono bloccate: attivale nelle impostazioni Android.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
