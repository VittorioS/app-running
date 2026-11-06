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
    private LinearLayout scheduleRows, cityResults;
    private final List<String> times = new ArrayList<>();
    private int searchSerial;
    private Switch enabled;
    private Button searchButton, forecastButton, saveButton;
    private WeatherApi.City selectedCity;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = AppPrefs.get(this);
        times.addAll(WeatherSchedule.decode(prefs.getString("weather_times", "")));
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
                Ui.text(this, "LOCALITÀ", 13, true, Ui.MUTED), 6);
        citySearch = field("Cerca città (es. Salerno)", "", false);
        citySearch.setSingleLine(true);
        // Campo di ricerca separato dalla localita' confermata e salvata.
        citySearch.setText("");
        Ui.marginBottom(this, card, citySearch, 5);

        searchButton = Ui.button(this, "Cerca località", false);
        searchButton.setOnClickListener(v -> searchCities());
        Ui.marginBottom(this, card, searchButton, 9);

        if (WeatherMonitor.hasCity(prefs)) {
            selectedCity = new WeatherApi.City(prefs.getString("weather_city", ""),
                    WeatherMonitor.latitude(prefs), WeatherMonitor.longitude(prefs));
        }
        selectedLabel = Ui.text(this, selectedCity == null
                ? "Nessuna località selezionata: cercane una qui sopra."
                : "Località attuale: " + selectedCity.title,
                14, true, Ui.GREEN);
        Ui.marginBottom(this, card, selectedLabel, 8);
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
        resultsView = Ui.text(this, "Scegli una città e tocca «Aggiorna condizioni meteo adesso».",
                13, false, Ui.MUTED);
        forecastCard.addView(resultsView);
        Ui.marginBottom(this, content, forecastCard, 12);

        Ui.marginBottom(this, content, Ui.text(this,
                "Le temperature e il vento sono STIME del modello meteo, non misure "
              + "del telefono. Gli orari sono quelli locali del telefono. Android "
              + "può ritardare i controlli programmati per il risparmio energetico, "
              + "quindi non è garantita l'ora esatta; è necessaria una connessione. "
              + "Con lista vuota non avvengono controlli automatici. "
              + "Massimo un avviso ogni 6 ore. Nessun GPS attivo e nessun servizio permanente.",
                13, false, Ui.MUTED), 12);
        Ui.marginBottom(this, content, Ui.text(this,
                "Dati meteo: Open-Meteo.com (previsioni, attribuzione al provider). "
              + "Servizio esterno: la città e le sue coordinate vengono usate per la richiesta.",
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
            if (enabled.isChecked() && selectedCity == null) {
                Toast.makeText(this, "Scegli prima una località", Toast.LENGTH_LONG).show();
                return;
            }
            if (enabled.isChecked() && times.isEmpty()) {
                Toast.makeText(this, "Aggiungi almeno un orario per attivare i controlli automatici",
                        Toast.LENGTH_LONG).show();
                return;
            }
            SharedPreferences.Editor change = prefs.edit()
                    .putString("weather_times", WeatherSchedule.encode(times))
                    .putBoolean("weather_enabled", enabled.isChecked())
                    .putString("weather_min_c", rules.minTemperatureC == null ? "" : rules.minTemperatureC.toString())
                    .putString("weather_max_c", rules.maxTemperatureC == null ? "" : rules.maxTemperatureC.toString())
                    .putString("weather_max_wind", rules.maxWindKmh == null ? "" : rules.maxWindKmh.toString());
            if (selectedCity != null) {
                change.putString("weather_city", selectedCity.title)
                        .putLong("weather_lat_bits",
                                Double.doubleToLongBits(selectedCity.latitude))
                        .putLong("weather_lon_bits",
                                Double.doubleToLongBits(selectedCity.longitude));
            }
            change.apply();
            WeatherMonitor.updateSchedule(this);
            Toast.makeText(this, enabled.isChecked()
                    ? "Criteri salvati: controllo meteo automatico attivo"
                    : "Criteri salvati: notifiche disattivate", Toast.LENGTH_LONG).show();
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
                final List<WeatherApi.City> found = WeatherApi.findCities(query);
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
                    for (WeatherApi.City city : found) {
                        Button option = Ui.button(this, "📍 " + city.title, false);
                        option.setTextSize(14);
                        option.setContentDescription("Seleziona località " + city.title);
                        option.setOnClickListener(v -> {
                            selectedCity = city;
                            selectedLabel.setText("✓ Località selezionata: " + city.title);
                            searchStatus.setText("Località scelta. Premi «Salva condizioni e notifiche» "
                                    + "per confermare le impostazioni.");
                            cityResults.removeAllViews();
                            cityResults.setVisibility(View.GONE);
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
        if (selectedCity == null) {
            Toast.makeText(this, "Seleziona prima una città", Toast.LENGTH_LONG).show();
            return;
        }
        final WeatherRules rules;
        try { rules = readRules(); }
        catch (IllegalArgumentException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        WeatherApi.City city = selectedCity;
        forecastButton.setEnabled(false);
        resultsView.setText("Caricamento delle previsioni per " + city.title + "…");
        new Thread(() -> {
            try {
                WeatherApi.Forecast forecast = WeatherApi.forecast(city.latitude, city.longitude);
                long now = System.currentTimeMillis() / 1000L;
                DateTimeFormatter clock = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ITALY)
                        .withZone(forecast.zone);
                StringBuilder text = new StringBuilder("Aggiornato adesso · previsioni Open-Meteo per ")
                        .append(city.title).append("\n")
                        .append("✅ = tutti i criteri rispettati | ✕ = non rispettati\n\n");
                int count = 0, matching = 0;
                WeatherApi.Hour closest = null;
                for (WeatherApi.Hour hour : forecast.hours) {
                    if (hour.epochSec <= now && hour.epochSec + 3600 > now) {
                        closest = hour;
                        break;
                    }
                }
                if (closest != null) {
                    boolean goodNow = rules.matches(closest.temperatureC, closest.windKmh);
                    text.append(goodNow ? "✅ CONDIZIONI ADESSO FAVOREVOLI" :
                            "✕ CONDIZIONI ADESSO NON FAVOREVOLI")
                            .append(" (stima oraria) · ")
                            .append(String.format(Locale.ITALY, "%.1f °C", closest.temperatureC))
                            .append(" · vento ")
                            .append(String.format(Locale.ITALY, "%.0f km/h", closest.windKmh))
                            .append("\n\n");
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
                text.append("\n").append(matching).append(" ore favorevoli su ")
                        .append(count).append(" valutate.\n")
                        .append("Sono previsioni soggette a variazioni; non garantiscono "
                              + "le condizioni effettive.");
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    forecastButton.setEnabled(true);
                    resultsView.setText(text.toString());
                });
            } catch (Exception ex) {
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    forecastButton.setEnabled(true);
                    resultsView.setText("Previsioni non disponibili: " + ex.getMessage());
                });
            }
        }, "WeatherForecastPreview").start();
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
