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
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Schermata principale; tutte le operazioni lunghe sono nel TrackingService. */
public final class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 50;
    private SharedPreferences prefs;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TextView distanceView, timerView, paceView, speedView, stepsView, activityView, gpsView, thresholdLabel, learningView;
    private Button startButton, stopButton, resumeButton, teachWalk, teachRun;
    private Switch voiceSwitch, adaptiveSwitch;
    private SeekBar thresholdSlider;
    private Spinner checkpointSpinner;
    private boolean uiUpdating;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        prefs = AppPrefs.get(this);
        buildUi();
        refresh();
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
        LinearLayout contents = Ui.vertical(this);
        contents.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 28));
        scroll.addView(contents);

        TextView title = Ui.text(this, "PassoLibero", 30, true, Ui.INK);
        Ui.marginBottom(this, contents, title, 4);
        Ui.marginBottom(this, contents, Ui.text(this,
                "La tua corsa. I tuoi dati. Solo sul telefono.", 13, false, Ui.MUTED), 20);

        LinearLayout stats = Ui.card(this);
        stats.addView(Ui.text(this, "DISTANZA TOTALE", 12, true, Ui.MUTED));
        distanceView = Ui.text(this, "0,00 km", 42, true, Ui.INK);
        Ui.marginBottom(this, stats, distanceView, 12);
        LinearLayout row = Ui.horizontal(this);
        LinearLayout durationCell = Ui.vertical(this);
        durationCell.addView(Ui.text(this, "DURATA", 11, true, Ui.MUTED));
        timerView = Ui.text(this, "00:00", 24, true, Ui.INK); durationCell.addView(timerView);
        Ui.weighted(row, durationCell);
        LinearLayout paceCell = Ui.vertical(this);
        paceCell.addView(Ui.text(this, "RITMO MEDIO", 11, true, Ui.MUTED));
        paceView = Ui.text(this, "--:-- /km", 24, true, Ui.INK); paceCell.addView(paceView);
        Ui.weighted(row, paceCell);
        stats.addView(row);
        Ui.marginBottom(this, contents, stats, 12);

        LinearLayout motion = Ui.card(this);
        activityView = Ui.text(this, "In attesa di una sessione", 20, true, Ui.GREEN);
        Ui.marginBottom(this, motion, activityView, 8);
        gpsView = Ui.text(this, "GPS non avviato", 13, false, Ui.MUTED);
        Ui.marginBottom(this, motion, gpsView, 12);
        LinearLayout motionStats = Ui.horizontal(this);
        LinearLayout l = Ui.vertical(this);
        l.addView(Ui.text(this, "VELOCITÀ", 11, true, Ui.MUTED));
        speedView = Ui.text(this, "0,0 km/h", 18, true, Ui.INK); l.addView(speedView);
        Ui.weighted(motionStats, l);
        LinearLayout r = Ui.vertical(this);
        r.addView(Ui.text(this, "PASSI", 11, true, Ui.MUTED));
        stepsView = Ui.text(this, "0", 18, true, Ui.INK); r.addView(stepsView);
        Ui.weighted(motionStats, r);
        motion.addView(motionStats);
        Ui.marginBottom(this, contents, motion, 12);

        startButton = Ui.button(this, "▶  Inizia sessione", true);
        startButton.setOnClickListener(v -> startRequested());
        Ui.marginBottom(this, contents, startButton, 8);
        resumeButton = Ui.button(this, "↻  Riprendi servizio interrotto", true);
        resumeButton.setOnClickListener(v -> startServiceAgain());
        Ui.marginBottom(this, contents, resumeButton, 8);
        stopButton = Ui.button(this, "■  Termina e salva", false);
        stopButton.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Terminare la sessione?")
                .setMessage("La sessione e tutti i punti GPS verranno salvati nello storico.")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Termina", (d, which) -> sendCommand(TrackingService.STOP)).show());
        Ui.marginBottom(this, contents, stopButton, 16);
        Button history = Ui.button(this, "Storico sessioni", false);
        history.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        Ui.marginBottom(this, contents, history, 22);

        Ui.marginBottom(this, contents, Ui.text(this, "IMPOSTAZIONI", 14, true, Ui.INK), 12);
        LinearLayout settingsCard = Ui.card(this);
        thresholdLabel = Ui.text(this, "Soglia: 6,0 km/h", 16, true, Ui.INK);
        settingsCard.addView(thresholdLabel);
        Ui.marginBottom(this, settingsCard, Ui.text(this,
                "Sotto = camminata, sopra = corsa. Isteresi ±0,5 km/h.", 12, false, Ui.MUTED), 8);
        thresholdSlider = new SeekBar(this);
        thresholdSlider.setMax(18);  // 3,0 .. 12,0 km/h con passi da 0,5
        thresholdSlider.setProgress((prefs.getInt("threshold_tenths", 60) - 30) / 5);
        thresholdSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int tenths = 30 + progress * 5;
                thresholdLabel.setText("Soglia: " + RunMath.velocity(tenths / 10.0) + " km/h");
                if (fromUser) prefs.edit().putInt("threshold_tenths", tenths).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        Ui.marginBottom(this, settingsCard, thresholdSlider, 8);
        adaptiveSwitch = new Switch(this);
        adaptiveSwitch.setText("Apprendimento locale attivo");
        adaptiveSwitch.setTextColor(Ui.INK);
        adaptiveSwitch.setChecked(AppPrefs.adaptiveEnabled(prefs));
        adaptiveSwitch.setOnCheckedChangeListener((button, on) -> prefs.edit().putBoolean("adaptive", on).apply());
        Ui.marginBottom(this, settingsCard, adaptiveSwitch, 12);
        learningView = Ui.text(this, "Profilo non ancora appreso", 12, false, Ui.MUTED);
        Ui.marginBottom(this, settingsCard, learningView, 15);
        settingsCard.addView(Ui.text(this, "Annuncio vocale ogni", 15, true, Ui.INK));
        checkpointSpinner = new Spinner(this);
        String[] intervals = {"250 metri", "500 metri", "1 chilometro", "2 chilometri", "5 chilometri"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, intervals);
        checkpointSpinner.setAdapter(adapter);
        int currentInterval = AppPrefs.intervalM(prefs);
        int[] values = {250, 500, 1000, 2000, 5000};
        for (int i = 0; i < values.length; i++) if (values[i] == currentInterval) checkpointSpinner.setSelection(i);
        checkpointSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("interval_m", values[position]).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        Ui.marginBottom(this, settingsCard, checkpointSpinner, 8);
        voiceSwitch = new Switch(this);
        voiceSwitch.setText("Checkpoint vocali offline");
        voiceSwitch.setTextColor(Ui.INK);
        voiceSwitch.setChecked(AppPrefs.voiceEnabled(prefs));
        voiceSwitch.setOnCheckedChangeListener((button, on) -> prefs.edit().putBoolean("voice", on).apply());
        Ui.marginBottom(this, settingsCard, voiceSwitch, 15);
        Button reset = Ui.button(this, "Azzera apprendimento", false);
        reset.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Azzerare il profilo?")
                .setMessage("Cancellerai solo le medie apprese, non le sessioni salvate.")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Azzera", (d,w) -> {
                    // Non consentire il reset durante un servizio attivo per evitare un vecchio salvataggio in uscita.
                    if (prefs.getBoolean("active", false)) {
                        Toast.makeText(this, "Termina prima la sessione", Toast.LENGTH_LONG).show(); return;
                    }
                    AdaptiveProfile m = new AdaptiveProfile(); AppPrefs.saveProfile(prefs, m); refresh();
                }).show());
        Ui.marginBottom(this, settingsCard, reset, 8);
        Ui.marginBottom(this, contents, settingsCard, 12);

        LinearLayout teachCard = Ui.card(this);
        Ui.marginBottom(this, teachCard, Ui.text(this, "Insegna il tuo movimento", 17, true, Ui.INK), 6);
        Ui.marginBottom(this, teachCard, Ui.text(this,
                "Durante una sessione, scegli l'attività che stai svolgendo. "
                        + "L'app raccoglie 30 secondi di velocità GPS affidabile per adattare il profilo.", 12, false, Ui.MUTED), 12);
        teachWalk = Ui.button(this, "Sto camminando", false);
        teachWalk.setOnClickListener(v -> { sendCommand(TrackingService.LABEL_WALK);
            Toast.makeText(this, "Campione camminata: 30 secondi", Toast.LENGTH_SHORT).show(); });
        Ui.marginBottom(this, teachCard, teachWalk, 8);
        teachRun = Ui.button(this, "Sto correndo", false);
        teachRun.setOnClickListener(v -> { sendCommand(TrackingService.LABEL_RUN);
            Toast.makeText(this, "Campione corsa: 30 secondi", Toast.LENGTH_SHORT).show(); });
        Ui.marginBottom(this, teachCard, teachRun, 0);
        Ui.marginBottom(this, contents, teachCard, 12);
        TextView footnote = Ui.text(this,
                "Nessun account, pubblicità o invio dei dati. Il tracciamento richiede una sessione avviata manualmente e mostra una notifica di sistema.",
                12, false, Ui.MUTED);
        footnote.setGravity(Gravity.CENTER);
        contents.addView(footnote);
    }

    private void startRequested() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new android.app.AlertDialog.Builder(this)
                    .setMessage("Attiva la posizione del telefono, poi avvia la sessione.")
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
        } else {
            startTracking();
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == REQUEST_PERMISSIONS) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startTracking();
            } else {
                Toast.makeText(this, "Serve la posizione precisa per il GPS.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startTracking() {
        if (prefs.getBoolean("active", false)) return;
        try {
            Intent i = new Intent(this, TrackingService.class).setAction(TrackingService.START);
            startForegroundService(i);
        } catch (RuntimeException ex) {
            Toast.makeText(this, "Avvio impedito dal sistema: " + ex.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void startServiceAgain() {
        // Nuovo avvio richiesto dalla schermata visibile; recupera la sessione e
        // non ricostruisce i punti GPS perduti quando il sistema l'ha interrotta.
        try {
            startForegroundService(new Intent(this, TrackingService.class).setAction(TrackingService.START));
        } catch (RuntimeException ex) {
            Toast.makeText(this, "Ripresa non disponibile: " + ex.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void sendCommand(String action) {
        startService(new Intent(this, TrackingService.class).setAction(action));
    }

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() { refresh(); uiHandler.postDelayed(this, 1000); }
    };

    private void refresh() {
        boolean active = prefs.getBoolean("active", false);
        RunState s = RunState.read(prefs);
        long elapsed = active ? Math.max(0, SystemClock.elapsedRealtime() - s.startedElapsedMs)
                : 0;
        distanceView.setText(RunMath.km(active ? s.distanceM : 0) + " km");
        timerView.setText(RunMath.duration(elapsed));
        paceView.setText(RunMath.pace(elapsed, s.distanceM) + " /km");
        speedView.setText(RunMath.velocity(active ? s.speedKmh : 0) + " km/h");
        stepsView.setText(Integer.toString(active ? s.steps : 0));
        String motion;
        switch (active ? s.movement : "UNKNOWN") {
            case "WALK": motion = "🚶 Camminata"; break;
            case "RUN": motion = "🏃 Corsa"; break;
            case "STILL": motion = "⏸ Fermo (sessione attiva)"; break;
            default: motion = active ? "Analisi del movimento…" : "Nessuna sessione attiva";
        }
        activityView.setText(motion);
        String error = prefs.getString("error", "");
        gpsView.setText(active ? s.gpsStatus : error.isEmpty()
                ? "Pronto a registrare anche a schermo spento" : error);
        startButton.setVisibility(active ? View.GONE : View.VISIBLE);
        boolean stale = active && System.currentTimeMillis() - prefs.getLong("heartbeat_wall", 0) > 15_000;
        resumeButton.setVisibility(stale ? View.VISIBLE : View.GONE);
        stopButton.setVisibility(active ? View.VISIBLE : View.GONE);
        teachWalk.setEnabled(active);
        teachRun.setEnabled(active);
        AdaptiveProfile p = AppPrefs.loadProfile(prefs);
        double base = AppPrefs.manualThreshold(prefs);
        double learned = p.threshold(base, AppPrefs.adaptiveEnabled(prefs));
        learningView.setText("Soglia effettiva: " + RunMath.velocity(learned) + " km/h  •  "
                + "campioni: " + p.walkSamples + " camminata / " + p.runSamples + " corsa");
    }

    @Override protected void onResume() {
        super.onResume();
        uiHandler.removeCallbacks(refreshRunnable);
        uiHandler.post(refreshRunnable);
    }
    @Override protected void onPause() {
        uiHandler.removeCallbacks(refreshRunnable);
        super.onPause();
    }
}
