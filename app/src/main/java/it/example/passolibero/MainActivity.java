package it.example.passolibero;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Home: riepilogo allenamento e impostazioni; la mappa è nella sessione corrente. */
public final class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 50;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView distanceView, durationView, statusView, thresholdLabel;
    private Button startButton, activeButton, resumeButton;
    private boolean resumed;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        prefs = AppPrefs.get(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        setContentView(root);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll);
        LinearLayout content = Ui.vertical(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 32));
        scroll.addView(content);

        Ui.marginBottom(this, content, Ui.text(this, "PassoLibero", 28, true, Ui.INK), 4);
        Ui.marginBottom(this, content,
                Ui.text(this, "Il tuo allenamento, sotto controllo", 14, false, Ui.MUTED), 12);

        LinearLayout summary = Ui.card(this);
        Ui.marginBottom(this, summary, Ui.text(this, "DISTANZA SESSIONE", 11, true, Ui.MUTED), 2);
        distanceView = Ui.text(this, "0,00 km", 36, true, Ui.INK);
        Ui.marginBottom(this, summary, distanceView, 10);
        Ui.marginBottom(this, summary, Ui.text(this, "DURATA", 11, true, Ui.MUTED), 2);
        durationView = Ui.text(this, "00:00", 23, true, Ui.INK);
        Ui.marginBottom(this, summary, durationView, 8);
        statusView = Ui.text(this, "Pronto per la prossima sessione", 13, false, Ui.MUTED);
        summary.addView(statusView);
        Ui.marginBottom(this, content, summary, 12);

        startButton = Ui.button(this, "▶  Inizia sessione", true);
        startButton.setOnClickListener(v -> startRequested());
        Ui.marginBottom(this, content, startButton, 9);
        activeButton = Ui.button(this, "📈  Vai alla sessione corrente", true);
        activeButton.setOnClickListener(v -> openSession());
        Ui.marginBottom(this, content, activeButton, 9);
        resumeButton = Ui.button(this, "↻  Riprendi tracciamento interrotto", false);
        resumeButton.setOnClickListener(v -> startServiceAgain());
        Ui.marginBottom(this, content, resumeButton, 9);
        Button history = Ui.button(this, "📚  Storico delle sessioni", false);
        history.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        Ui.marginBottom(this, content, history, 18);

        Ui.marginBottom(this, content, Ui.text(this, "IMPOSTAZIONI DI ALLENAMENTO", 14, true, Ui.INK), 10);
        LinearLayout settings = Ui.card(this);
        thresholdLabel = Ui.text(this, "Soglia corsa/camminata", 15, true, Ui.INK);
        Ui.marginBottom(this, settings, thresholdLabel, 7);
        SeekBar slider = new SeekBar(this);
        slider.setMax(18);  // da 3 a 12 km/h, passo 0,5
        slider.setProgress(Math.min(18, Math.max(0, (prefs.getInt("threshold_tenths", 60) - 30) / 5)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                int tenths = 30 + progress * 5;
                thresholdLabel.setText("Passaggio alla corsa: " + RunMath.velocity(tenths / 10.0) + " km/h");
                if (user) prefs.edit().putInt("threshold_tenths", tenths).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        Ui.marginBottom(this, settings, slider, 5);
        Switch adaptive = new Switch(this);
        adaptive.setText("Classificazione adattiva sul telefono");
        adaptive.setChecked(AppPrefs.adaptiveEnabled(prefs));
        adaptive.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("adaptive", on).apply());
        Ui.marginBottom(this, settings, adaptive, 10);
        Switch voice = new Switch(this);
        voice.setText("Annunci vocali ai checkpoint");
        voice.setChecked(AppPrefs.voiceEnabled(prefs));
        voice.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("voice", on).apply());
        Ui.marginBottom(this, settings, voice, 0);
        Ui.marginBottom(this, content, settings, 12);
    }

    private void startRequested() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new android.app.AlertDialog.Builder(this).setMessage("Attiva la posizione del telefono prima di correre.")
                    .setPositiveButton("Impostazioni", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Annulla", null).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION,
                    Manifest.permission.POST_NOTIFICATIONS}, REQUEST_PERMISSIONS);
        } else startTracking();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != REQUEST_PERMISSIONS) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startTracking();
        else Toast.makeText(this, "Per il percorso serve la posizione precisa.", Toast.LENGTH_LONG).show();
    }

    private void startTracking() {
        if (prefs.getBoolean("active", false)) { openSession(); return; }
        try {
            startForegroundService(new Intent(this, TrackingService.class).setAction(TrackingService.START));
            openSession();  // Il servizio inizializza i dati nei secondi immediatamente successivi.
        } catch (RuntimeException ex) {
            Toast.makeText(this, "Avvio non riuscito: " + ex.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void startServiceAgain() {
        try {
            startForegroundService(new Intent(this, TrackingService.class).setAction(TrackingService.START));
            openSession();
        } catch (RuntimeException ex) {
            Toast.makeText(this, "Ripresa non riuscita", Toast.LENGTH_LONG).show();
        }
    }

    private void openSession() {
        startActivity(new Intent(this, ActiveSessionActivity.class));
    }

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            boolean active = prefs.getBoolean("active", false);
            RunState state = RunState.read(prefs);
            distanceView.setText((active ? RunMath.km(state.distanceM) : "0,00") + " km");
            durationView.setText(active ? RunMath.duration(Math.max(0, SystemClock.elapsedRealtime() - state.startedElapsedMs)) : "00:00");
            statusView.setText(active ? state.gpsStatus + " · " + formatMovement(state.movement)
                    : "Nessuna sessione attiva. Avvia per registrare un allenamento.");
            startButton.setVisibility(active ? android.view.View.GONE : android.view.View.VISIBLE);
            activeButton.setVisibility(active ? android.view.View.VISIBLE : android.view.View.GONE);
            boolean stale = active && System.currentTimeMillis() - prefs.getLong("heartbeat_wall", 0) > 15_000;
            resumeButton.setVisibility(stale ? android.view.View.VISIBLE : android.view.View.GONE);
            handler.postDelayed(this, 1500);
        }
    };

    private static String formatMovement(String code) {
        if ("RUN".equals(code)) return "Corsa";
        if ("WALK".equals(code)) return "Camminata";
        if ("STILL".equals(code)) return "Fermo";
        return "In analisi";
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }
    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }
}
