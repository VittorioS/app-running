package it.example.passolibero;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;

/** Compilata solo nelle build debug. Registrazione opzionale, mai automatica. */
public final class BatteryDiagnosticsActivity extends Activity {
    private static final int SAVE_CSV = 734;
    private static final int[] DURATIONS_SEC = {10, 30, 60, 300, 600};
    private static final String[] SCENARIOS = {
            "Riferimento - nessuna sessione",
            "Camminata - sessione attiva",
            "Corsa - mappa visibile",
            "Corsa - mappa non visibile"
    };
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Spinner scenarios, durations;
    private TextView status;
    private Button start, stop, export;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
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
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 20),
                Ui.dp(this, 18), Ui.dp(this, 32));
        scroll.addView(content);

        Ui.marginBottom(this, content, Ui.text(this,
                "Diagnostica batteria · DEBUG", 23, true, Ui.INK), 10);
        Ui.marginBottom(this, content, Ui.text(this,
                "Registra un campione al secondo. Dopo Avvia puoi tornare alla sessione: "
              + "la registrazione continua fino al termine anche a schermata chiusa.",
                14, false, Ui.MUTED), 14);

        LinearLayout card = Ui.card(this);
        Ui.marginBottom(this, card,
                Ui.text(this, "Scenario (etichetta di confronto)", 14, true, Ui.INK), 7);
        scenarios = new Spinner(this);
        scenarios.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, SCENARIOS));
        Ui.marginBottom(this, card, scenarios, 12);

        Ui.marginBottom(this, card, Ui.text(this,
                "Durata della registrazione", 14, true, Ui.INK), 7);
        durations = new Spinner(this);
        durations.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"10 secondi", "30 secondi", "60 secondi",
                        "5 minuti", "10 minuti"}));
        durations.setSelection(1);
        Ui.marginBottom(this, card, durations, 12);

        status = Ui.text(this, "Nessuna registrazione", 14, true, Ui.GREEN);
        Ui.marginBottom(this, card, status, 12);
        start = Ui.button(this, "Avvia acquisizione", true);
        start.setOnClickListener(v -> {
            int scenario = scenarios.getSelectedItemPosition();
            boolean active = AppPrefs.get(this).getBoolean("active", false);
            if (scenario == 0 && active) {
                Toast.makeText(this, "Per il riferimento termina prima la sessione.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (scenario != 0 && !active) {
                Toast.makeText(this, "Avvia prima un allenamento.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            BatteryRecorder.start(getApplicationContext(),
                    DURATIONS_SEC[durations.getSelectedItemPosition()],
                    SCENARIOS[scenario]);
            refreshUi();
        });
        Ui.marginBottom(this, card, start, 8);

        stop = Ui.button(this, "Interrompi acquisizione", false);
        stop.setOnClickListener(v -> {
            BatteryRecorder.stop();
            refreshUi();
        });
        Ui.marginBottom(this, card, stop, 8);

        export = Ui.button(this, "Salva un unico file CSV", false);
        export.setOnClickListener(v -> {
            if (!BatteryRecorder.hasReport()) return;
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            save.addCategory(Intent.CATEGORY_OPENABLE);
            save.setType("text/csv");
            save.putExtra(Intent.EXTRA_TITLE,
                    "passolibero-batteria-" + System.currentTimeMillis() + ".csv");
            startActivityForResult(save, SAVE_CSV);
        });
        Ui.marginBottom(this, card, export, 0);
        Ui.marginBottom(this, content, card, 14);

        Ui.marginBottom(this, content, Ui.text(this,
                "Dati REALI: corrente, carica e tensione dell'INTERO telefono quando "
              + "Android li espone; CPU, memoria e traffico dati dell'app. "
              + "Inoltre: schermo, stato termico, GPS, velocità e passi. "
              + "I valori mancanti restano vuoti, mai inventati.",
                13, false, Ui.MUTED), 12);
        Ui.marginBottom(this, content, Ui.text(this,
                "Non misura direttamente l'energia imputabile alla singola app. "
              + "Per i confronti usa luminosità, rete, temperatura e batteria "
              + "simili, senza caricabatterie. L'etichetta dello scenario è manuale. "
              + "Il CSV non contiene coordinate GPS; il registratore stesso ha "
              + "un piccolo costo energetico e si interrompe se Android chiude l'app.",
                13, false, Ui.MUTED), 12);

        Button back = Ui.button(this, "← Torna all'app", false);
        back.setOnClickListener(v -> finish());
        Ui.marginBottom(this, content, back, 0);
    }

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshUi();
            ui.postDelayed(this, 500);
        }
    };

    private void refreshUi() {
        boolean recording = BatteryRecorder.isRunning();
        status.setText(BatteryRecorder.status());
        start.setEnabled(!recording);
        stop.setEnabled(recording);
        export.setEnabled(!recording && BatteryRecorder.hasReport());
        scenarios.setEnabled(!recording);
        durations.setEnabled(!recording);
    }

    @Override protected void onResume() {
        super.onResume();
        ui.removeCallbacks(refresh);
        ui.post(refresh);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(refresh);
        super.onPause();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_CSV || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        String csv = BatteryRecorder.csv();
        if (csv == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IOException("Memoria di destinazione non disponibile");
            out.write(csv.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "CSV salvato; ora puoi inviarmelo.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, "Esportazione non riuscita: " + ex.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}

/** Campionatore debug unico per il processo, nessun servizio GPS addizionale. */
final class BatteryRecorder {
    private static final Object LOCK = new Object();
    private static final String HEADER =
            "utc_iso,elapsed_ms,actual_interval_ms,scenario,planned_seconds,"
          + "manufacturer,model,android_api,app_version,"
          + "session_active,movement,speed_kmh,gps_accuracy_m,gps_status,"
          + "steps,cadence_spm,distance_m,voice_enabled,"
          + "screen_interactive,screen_brightness_setting,network_transport,thermal_status,"
          + "battery_level_pct,battery_charging,battery_temperature_c,battery_voltage_mv,"
          + "phone_current_now_ua,phone_current_average_ua,"
          + "phone_charge_counter_uah,phone_energy_counter_nwh,"
          + "app_process_cpu_total_ms,app_process_cpu_delta_ms,app_process_pss_kb,"
          + "app_uid_rx_delta_bytes,app_uid_tx_delta_bytes\n";

    private static HandlerThread thread;
    private static Handler worker;
    private static Context app;
    private static boolean running;
    private static long startedElapsed, startedWall, lastSampleElapsed;
    private static long cpuPrevious, rxPrevious, txPrevious;
    private static int durationSeconds, samples;
    private static String scenario, report;
    private static StringBuilder rows;

    static void start(Context context, int seconds, String requestedScenario) {
        synchronized (LOCK) {
            if (running) return;
            app = context.getApplicationContext();
            durationSeconds = seconds;
            scenario = requestedScenario;
            startedElapsed = SystemClock.elapsedRealtime();
            startedWall = System.currentTimeMillis();
            lastSampleElapsed = -1;
            cpuPrevious = -1;
            rxPrevious = -1;
            txPrevious = -1;
            samples = 0;
            report = null;
            rows = new StringBuilder(HEADER);
            running = true;
            thread = new HandlerThread("BatteryDebugSampler",
                    Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            worker = new Handler(thread.getLooper());
            worker.post(SAMPLE);
        }
    }

    static void stop() {
        synchronized (LOCK) {
            if (running) complete();
        }
    }

    private static void complete() {
        running = false;
        report = rows == null ? null : rows.toString();
        if (worker != null) worker.removeCallbacks(SAMPLE);
        if (thread != null) thread.quitSafely();
        worker = null;
        thread = null;
    }

    static boolean isRunning() {
        synchronized (LOCK) { return running; }
    }
    static boolean hasReport() {
        synchronized (LOCK) { return report != null; }
    }
    static String csv() {
        synchronized (LOCK) { return report; }
    }
    static String status() {
        synchronized (LOCK) {
            if (running) {
                long elapsed = SystemClock.elapsedRealtime() - startedElapsed;
                return "Acquisizione " + Math.min(durationSeconds, elapsed / 1000)
                        + " / " + durationSeconds + " s · " + samples + " campioni";
            }
            return report == null ? "Nessuna registrazione"
                    : "Completata · " + samples + " campioni · CSV pronto";
        }
    }

    private static final Runnable SAMPLE = new Runnable() {
        @Override public void run() {
            synchronized (LOCK) {
                if (!running) return;
                long now = SystemClock.elapsedRealtime();
                long elapsed = now - startedElapsed;
                try {
                    rows.append(readSample(app, elapsed, now)).append('\n');
                    samples++;
                } catch (RuntimeException ex) {
                    android.util.Log.w("BatteryDebug", "Errore nel campionamento", ex);
                    complete();  // Conserva il CSV parziale.
                    return;
                }
                if (elapsed >= durationSeconds * 1000L) complete();
                else if (worker != null) {
                    long next = startedElapsed + samples * 1000L;
                    worker.postDelayed(this,
                            Math.max(0, next - SystemClock.elapsedRealtime()));
                }
            }
        }
    };

    private static String readSample(Context context, long elapsed, long nowElapsed) {
        SharedPreferences prefs = AppPrefs.get(context);
        boolean active = prefs.getBoolean("active", false);
        RunState state = RunState.read(prefs);
        BatteryManager battery =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        Intent batteryChanged =
                context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        PowerManager power =
                (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities network = connectivity == null ? null
                : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        String transport = network == null ? "none"
                : network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "wifi"
                : network.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "cellular"
                : network.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "ethernet"
                : "other";

        long cpuMs = Process.getElapsedCpuTime();
        long rx = TrafficStats.getUidRxBytes(Process.myUid());
        long tx = TrafficStats.getUidTxBytes(Process.myUid());
        String cpuDelta = cpuPrevious < 0 ? "" : Long.toString(Math.max(0, cpuMs - cpuPrevious));
        String rxDelta = bytesDelta(rx, rxPrevious);
        String txDelta = bytesDelta(tx, txPrevious);
        cpuPrevious = cpuMs;
        rxPrevious = rx;
        txPrevious = tx;

        int level = extra(batteryChanged, BatteryManager.EXTRA_LEVEL, -1);
        int scale = extra(batteryChanged, BatteryManager.EXTRA_SCALE, -1);
        int batteryStatus = extra(batteryChanged, BatteryManager.EXTRA_STATUS, -1);
        int temp = extra(batteryChanged, BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        int voltage = extra(batteryChanged, BatteryManager.EXTRA_VOLTAGE, -1);

        ArrayList<String> values = new ArrayList<>();
        values.add(Instant.ofEpochMilli(startedWall + elapsed).toString());
        values.add(Long.toString(elapsed));
        values.add(lastSampleElapsed < 0 ? "" : Long.toString(nowElapsed - lastSampleElapsed));
        lastSampleElapsed = nowElapsed;
        values.add(scenario);
        values.add(Integer.toString(durationSeconds));
        values.add(Build.MANUFACTURER);
        values.add(Build.MODEL);
        values.add(Integer.toString(Build.VERSION.SDK_INT));
        values.add(BuildConfig.VERSION_NAME);

        values.add(active ? "1" : "0");
        values.add(active ? state.movement : "");
        values.add(active ? number(state.speedKmh) : "");
        values.add(active && state.lastLocationValid ? number(state.gpsAccuracy) : "");
        values.add(active ? state.gpsStatus : "");
        values.add(active ? Integer.toString(state.steps) : "");
        values.add(active ? Integer.toString(state.cadenceSpm) : "");
        values.add(active ? number(state.distanceM) : "");
        values.add(AppPrefs.voiceEnabled(prefs) ? "1" : "0");

        values.add(power == null ? "" : power.isInteractive() ? "1" : "0");
        int brightness = Settings.System.getInt(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, -1);
        values.add(brightness < 0 ? "" : Integer.toString(brightness));
        values.add(transport);
        values.add(power == null ? "" : Integer.toString(power.getCurrentThermalStatus()));

        values.add(level < 0 || scale <= 0 ? "" : number(100.0 * level / scale));
        values.add(batteryStatus < 0 ? "" : (batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
                || batteryStatus == BatteryManager.BATTERY_STATUS_FULL ? "1" : "0"));
        values.add(temp == Integer.MIN_VALUE ? "" : number(temp / 10.0));
        values.add(voltage < 0 ? "" : Integer.toString(voltage));
        values.add(batteryInt(battery, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
        values.add(batteryInt(battery, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE));
        values.add(batteryInt(battery, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
        values.add(batteryLong(battery, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER));
        values.add(Long.toString(cpuMs));
        values.add(cpuDelta);
        values.add(Integer.toString(Debug.getPss()));
        values.add(rxDelta);
        values.add(txDelta);

        StringBuilder line = new StringBuilder(256);
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) line.append(',');
            line.append(csvValue(values.get(i)));
        }
        return line.toString();
    }

    private static int extra(Intent intent, String key, int missing) {
        return intent == null ? missing : intent.getIntExtra(key, missing);
    }

    private static String bytesDelta(long current, long previous) {
        if (current == TrafficStats.UNSUPPORTED || current < 0 || previous < 0
                || current < previous) return "";
        return Long.toString(current - previous);
    }

    private static String batteryInt(BatteryManager manager, int what) {
        if (manager == null) return "";
        int value = manager.getIntProperty(what);
        if (value == Integer.MIN_VALUE) return "";
        if (what == BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER && value < 0) return "";
        return Integer.toString(value);
    }

    private static String batteryLong(BatteryManager manager, int what) {
        if (manager == null) return "";
        long value = manager.getLongProperty(what);
        return value < 0 ? "" : Long.toString(value);
    }

    private static String number(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.2f", value) : "";
    }

    private static String csvValue(String value) {
        if (value == null) return "";
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
