package it.example.passolibero;

import android.app.Activity;
import android.graphics.Rect;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/** Statistiche della sessione corrente: ritmo espresso SEMPRE in minuti/km. */
public final class ActiveSessionActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TrackerDb db;
    private TrackMap map;
    private TextView distance, paceNow, paceAvg, duration, speed, averageSpeed, steps,
            cadence, tabTitle, tabHint, unclassifiedHint, movement, gps, checkpoint, remaining, goalLabel;
    private Button[] tabButtons;
    private Button trainingButton, exportJsonButton;
    private SessionTabStats.Tab currentTab = SessionTabStats.Tab.TOTAL;
    private ProgressBar goalProgress;
    private boolean resumed, sessionSeen;
    // Consente una sola selezione automatica dopo onResume. Un tap manuale la annulla.
    private boolean pendingResumeTabSelection;
    private boolean mapRendered;
    private boolean gpsDialogShown;
    private long pendingJsonExportSessionId;

    private static final int[] CHECKPOINTS = {250, 500, 1000, 2000, 5000};
    private static final int[] GOALS = {0, 1000, 2000, 3000, 5000, 10000, 21097, -1};

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        prefs = AppPrefs.get(this);
        db = new TrackerDb(this);
        if (savedState != null) {
            pendingJsonExportSessionId = savedState.getLong("json_export_session_id", 0);
            int index = savedState.getInt("stats_tab", SessionTabStats.Tab.TOTAL.ordinal());
            if (index >= 0 && index < SessionTabStats.Tab.values().length)
                currentTab = SessionTabStats.Tab.values()[index];
        }
        buildUi();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("stats_tab", currentTab.ordinal());
        outState.putLong("json_export_session_id", pendingJsonExportSessionId);
        super.onSaveInstanceState(outState);
    }

    private TextView stat(LinearLayout parent, String name, String initial) {
        Ui.marginBottom(this, parent, Ui.text(this, name, 11, true, Ui.MUTED), 1);
        TextView v = Ui.text(this, initial, 21, true, Ui.INK);
        Ui.marginBottom(this, parent, v, 12);
        return v;
    }

    private void buildUi() {
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v, i) -> {
            android.graphics.Insets b = i.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(0, b.top, 0, b.bottom);
            return i;
        });
        setContentView(root);
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll);
        LinearLayout content = Ui.vertical(this);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 25));
        scroll.addView(content);

        Ui.marginBottom(this, content, Ui.text(this, "Sessione in corso", 27, true, Ui.INK), 5);
        movement = Ui.text(this, "Avvio del tracciamento…", 16, true, Ui.GREEN);
        Ui.marginBottom(this, content, movement, 3);
        gps = Ui.text(this, "In attesa del GPS", 13, false, Ui.MUTED);
        Ui.marginBottom(this, content, gps, 14);

        if (BuildConfig.DEBUG) {
            // La schermata esiste soltanto nel source set debug; nessun riferimento
            // Java diretto, per poter compilare anche la variante release.
            Button batteryDebug = Ui.button(this, "🔋 Diagnostica batteria (DEBUG)", false);
            batteryDebug.setOnClickListener(v -> startActivity(new Intent().setClassName(
                    this, getPackageName() + ".BatteryDiagnosticsActivity")));
            Ui.marginBottom(this, content, batteryDebug, 12);
        }

        // Le tre tab modificano solo la visualizzazione: il classificatore GPS resta invariato.
        LinearLayout tabBar = Ui.horizontal(this);
        tabButtons = new Button[3];
        String[] labels = {"🏃 Corsa", "🚶 Camminata", "∑ Totali"};
        SessionTabStats.Tab[] tabs = SessionTabStats.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            final SessionTabStats.Tab tab = tabs[i];
            Button tabButton = Ui.button(this, labels[i], false);
            tabButton.setTextSize(13);
            tabButton.setContentDescription("Statistiche " + labels[i]);
            tabButton.setOnClickListener(v -> {
                pendingResumeTabSelection = false;
                currentTab = tab;
                updateTabButtons();
                renderTab(RunState.read(prefs), prefs.getBoolean("active", false));
            });
            tabButtons[i] = tabButton;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f);
            lp.rightMargin = Ui.dp(this, i == 2 ? 0 : 5);
            tabBar.addView(tabButton, lp);
        }
        Ui.marginBottom(this, content, tabBar, 12);
        updateTabButtons();

        LinearLayout statsCard = Ui.card(this);
        tabTitle = Ui.text(this, "TOTALI CORSA + CAMMINATA", 14, true, Ui.MUTED);
        Ui.marginBottom(this, statsCard, tabTitle, 4);
        distance = Ui.text(this, "0,00 km", 43, true, Ui.INK);
        Ui.marginBottom(this, statsCard, distance, 12);
        duration = stat(statsCard, "TEMPO", "00:00");
        paceAvg = stat(statsCard, "RITMO MEDIO (min/km)", "--:-- min/km");
        averageSpeed = stat(statsCard, "VELOCITÀ MEDIA", "-- km/h");
        paceNow = stat(statsCard, "RITMO ATTUALE (min/km)", "--:-- min/km");
        speed = stat(statsCard, "VELOCITÀ ATTUALE", "-- km/h");
        steps = stat(statsCard, "PASSI ATTRIBUITI", "0");
        cadence = stat(statsCard, "CADENZA MEDIA", "-- passi/min");
        tabHint = Ui.text(this, "", 13, false, Ui.MUTED);
        Ui.marginBottom(this, statsCard, tabHint, 8);
        unclassifiedHint = Ui.text(this, "", 12, false, Ui.MUTED);
        Ui.marginBottom(this, statsCard, unclassifiedHint, 0);
        // Un solo pulsante contestuale: l'azione dipende dalla tab selezionata.
        // Nessun pulsante di addestramento nella tab Totali.
        trainingButton = Ui.button(this, "Insegna all'app: sto correndo", false);
        trainingButton.setOnClickListener(v -> {
            if (currentTab == SessionTabStats.Tab.RUN) {
                label(TrackingService.LABEL_RUN);
            } else if (currentTab == SessionTabStats.Tab.WALK) {
                label(TrackingService.LABEL_WALK);
            }
        });
        Ui.marginBottom(this, statsCard, trainingButton, 0);
        Ui.marginBottom(this, content, statsCard, 12);

        exportJsonButton = Ui.button(this, "↓ Esporta sessione in JSON", false);
        exportJsonButton.setOnClickListener(v -> {
            RunState state = RunState.read(prefs);
            if (state.id <= 0 || !prefs.getBoolean("active", false)) {
                Toast.makeText(this, "Nessuna sessione attiva da esportare", Toast.LENGTH_LONG).show();
                return;
            }
            pendingJsonExportSessionId = state.id;
            SessionJsonExporter.requestSave(this, state.id);
        });
        Ui.marginBottom(this, content, exportJsonButton, 5);
        Ui.marginBottom(this, content, Ui.text(this,
                "Include statistiche e coordinate GPS precise: scegli dove salvare il file.",
                12, false, Ui.MUTED), 12);

        // La sola mappa live dell'app: posizione corrente e punti GPS di questa sessione.
        LinearLayout positionCard = Ui.card(this);
        Ui.marginBottom(this, positionCard,
                Ui.text(this, "LA TUA POSIZIONE · MAPPA", 14, true, Ui.INK), 8);
        map = new TrackMap(this);
        map.setAutoFollowCurrentPosition(true);
        positionCard.addView(map.view(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 300)));
        Ui.marginBottom(this, positionCard, Ui.text(this,
                "© OpenStreetMap contributors · La mappa segue la posizione GPS attuale",
                11, false, Ui.MUTED), 0);
        Ui.marginBottom(this, content, positionCard, 12);

        LinearLayout progressCard = Ui.card(this);
        Ui.marginBottom(this, progressCard, Ui.text(this, "PROSSIMO CHECKPOINT", 14, true, Ui.INK), 6);
        checkpoint = stat(progressCard, "SOGLIA", "1000 m");
        remaining = stat(progressCard, "DISTANZA RIMANENTE", "1000 m");
        Ui.marginBottom(this, progressCard, Ui.text(this, "MODIFICA INTERVALLO CHECKPOINT", 11, true, Ui.MUTED), 4);
        Spinner intervals = new Spinner(this);
        intervals.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"250 m", "500 m", "1 km", "2 km", "5 km"}));
        int interval = AppPrefs.intervalM(prefs);
        for (int i = 0; i < CHECKPOINTS.length; i++) if (CHECKPOINTS[i] == interval) intervals.setSelection(i);
        intervals.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int idx, long id) {
                prefs.edit().putInt("interval_m", CHECKPOINTS[idx]).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        Ui.marginBottom(this, progressCard, intervals, 12);

        Ui.marginBottom(this, progressCard, Ui.text(this, "OBIETTIVO DISTANZA", 14, true, Ui.INK), 6);
        goalLabel = Ui.text(this, "Nessun obiettivo impostato", 14, true, Ui.GREEN);
        Ui.marginBottom(this, progressCard, goalLabel, 7);
        goalProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        goalProgress.setMax(100);
        Ui.marginBottom(this, progressCard, goalProgress, 8);
        Spinner goals = new Spinner(this);
        goals.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Nessun obiettivo", "1 km", "2 km", "3 km", "5 km", "10 km", "Mezza maratona (21,1 km)", "Personalizzato"}));
        int goal = AppPrefs.goalM(prefs);
        int goalIndex = GOALS.length - 1;
        for (int i = 0; i < GOALS.length - 1; i++) if (GOALS[i] == goal) goalIndex = i;
        goals.setSelection(goalIndex);
        goals.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int idx, long id) {
                if (GOALS[idx] >= 0) prefs.edit().putInt("goal_m", GOALS[idx]).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        Ui.marginBottom(this, progressCard, goals, 6);
        EditText customGoal = new EditText(this);
        customGoal.setHint("Obiettivo personalizzato in km (es. 7,5)");
        customGoal.setSingleLine(true);
        customGoal.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Ui.marginBottom(this, progressCard, customGoal, 6);
        Button applyGoal = Ui.button(this, "Imposta chilometri personalizzati", false);
        applyGoal.setOnClickListener(v -> {
            try {
                double km = Double.parseDouble(customGoal.getText().toString().trim().replace(',', '.'));
                if (!Double.isFinite(km) || km < 0.1 || km > 200) throw new NumberFormatException();
                prefs.edit().putInt("goal_m", (int) Math.round(km * 1000)).apply();
                goals.setSelection(GOALS.length - 1);
                customGoal.clearFocus();
                Toast.makeText(this, "Obiettivo salvato", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Inserisci un valore da 0,1 a 200 km", Toast.LENGTH_LONG).show();
            }
        });
        Ui.marginBottom(this, progressCard, applyGoal, 3);
        Ui.marginBottom(this, content, progressCard, 12);

        Button home = Ui.button(this, "←  Torna alla Home", false);
        home.setOnClickListener(v -> finish());
        Ui.marginBottom(this, content, home, 10);
        Button stop = Ui.button(this, "■  Termina e salva la sessione", true);
        stop.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Terminare la sessione?")
                .setMessage("Percorso e statistiche saranno conservati nello storico.")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Termina", (d, w) ->
                        startService(new Intent(this, TrackingService.class).setAction(TrackingService.STOP)))
                .show());
        Ui.marginBottom(this, content, stop, 4);
    }


    private void updateTabButtons() {
        if (tabButtons == null) return;
        SessionTabStats.Tab[] tabs = SessionTabStats.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = tabs[i] == currentTab;
            tabButtons[i].setSelected(selected);
            tabButtons[i].setTextColor(selected ? android.graphics.Color.WHITE : Ui.GREEN);
            tabButtons[i].setBackground(Ui.shape(
                    selected ? Ui.GREEN : android.graphics.Color.rgb(230, 246, 242),
                    12, this));
        }
    }

    private void renderTab(RunState s, boolean active) {
        exportJsonButton.setEnabled(active && s.id > 0);
        SessionTabStats.Tab tab = currentTab;
        trainingButton.setVisibility(tab == SessionTabStats.Tab.TOTAL ? View.GONE : View.VISIBLE);
        trainingButton.setEnabled(active);
        if (tab == SessionTabStats.Tab.RUN) {
            trainingButton.setText("Insegna all'app: sto correndo");
        } else if (tab == SessionTabStats.Tab.WALK) {
            trainingButton.setText("Insegna all'app: sto camminando");
        }
        double meters = active ? SessionTabStats.meters(tab, s.runM, s.walkM) : 0;
        long milliseconds = active ? SessionTabStats.milliseconds(tab, s.runMs, s.walkMs) : 0;
        int classifiedSteps = active ? SessionTabStats.steps(tab, s.runSteps, s.walkSteps) : 0;
        boolean current = active && SessionTabStats.hasCurrentSpeed(tab, s.movement);
        tabTitle.setText(tab == SessionTabStats.Tab.RUN ? "SOLO CORSA" :
                tab == SessionTabStats.Tab.WALK ? "SOLO CAMMINATA" : "CORSA + CAMMINATA");
        distance.setText(RunMath.km(meters) + " km");
        duration.setText(RunMath.duration(milliseconds));
        paceAvg.setText(RunMath.pace(milliseconds, meters) + " min/km");
        double avgSpeed = SessionTabStats.averageSpeedKmh(milliseconds, meters);
        averageSpeed.setText(Double.isFinite(avgSpeed)
                ? RunMath.velocity(avgSpeed) + " km/h" : "-- km/h");
        paceNow.setText(current ? RunStats.instantPace(s.speedKmh) + " min/km" : "--:-- min/km");
        speed.setText(current ? RunMath.velocity(s.speedKmh) + " km/h" : "-- km/h");
        steps.setText(Integer.toString(classifiedSteps));
        double avgCadence = SessionTabStats.averageCadence(milliseconds, classifiedSteps);
        cadence.setText(Double.isFinite(avgCadence)
                ? Math.round(avgCadence) + " passi/min" : "-- passi/min");
        tabHint.setText(tab == SessionTabStats.Tab.RUN
                ? "Dati attribuiti esclusivamente ai tratti classificati come corsa."
                : tab == SessionTabStats.Tab.WALK
                ? "Dati attribuiti esclusivamente ai tratti classificati come camminata."
                : "Somma dei tratti classificati come corsa e camminata, senza periodi incerti.");
        if (tab == SessionTabStats.Tab.TOTAL) {
            int unclassifiedSteps = active ? Math.max(0, s.steps - s.runSteps - s.walkSteps) : 0;
            double unclassifiedMeters = active
                    ? Math.max(0, s.distanceM - s.runM - s.walkM) : 0;
            unclassifiedHint.setText("Fuori dalle due categorie: "
                    + unclassifiedSteps + " passi, " + RunStats.meters(unclassifiedMeters)
                    + "; fermo " + RunMath.duration(active ? s.stillMs : 0)
                    + ", movimento non classificato "
                    + RunMath.duration(active ? s.movingUnknownMs : 0)
                    + ", incerto " + RunMath.duration(active ? s.unknownMs : 0)
                    + ". Totale sessione: "
                    + RunMath.duration(active ? Math.max(0,
                    SystemClock.elapsedRealtime() - s.startedElapsedMs) : 0) + ".");
            unclassifiedHint.setVisibility(View.VISIBLE);
        } else {
            unclassifiedHint.setVisibility(View.GONE);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SessionJsonExporter.REQUEST_CODE) {
            SessionJsonExporter.onSaveResult(this, pendingJsonExportSessionId, resultCode, data);
            pendingJsonExportSessionId = 0;
        }
    }

    private void maybeShowGpsAlert(RunState s) {
        if (gpsDialogShown || !prefs.getBoolean("gps_ack_needed", false)) return;
        if (!"STILL".equals(s.movement)) return;
        gpsDialogShown = true;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Segnale GPS impreciso")
                .setMessage("Sei fermo e il segnale GPS non è affidabile. "
                        + "Puoi confermare per continuare oppure attendere un segnale migliore. "
                        + "Il tracciamento non viene interrotto.")
                .setPositiveButton("Ho capito, continua", (d, w) ->
                        prefs.edit().putBoolean("gps_ack_needed", false).apply())
                .setNegativeButton("Attendi GPS", (d, w) ->
                        prefs.edit().putBoolean("gps_ack_needed", false).apply())
                .setOnDismissListener(d -> gpsDialogShown = false)
                .show();
    }

    private void label(String type) {
        if (!prefs.getBoolean("active", false)) return;
        startService(new Intent(this, TrackingService.class).setAction(type));
        Toast.makeText(this, "Raccolgo 30 secondi di esempi", Toast.LENGTH_SHORT).show();
    }

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            boolean active = prefs.getBoolean("active", false);
            if (active) sessionSeen = true;
            if (sessionSeen && !active) { finish(); return; }
            RunState s = RunState.read(prefs);
            if (pendingResumeTabSelection && active) {
                // Non usare il movimento salvato prima di un riavvio del telefono:
                // deve esserci un tracking service che aggiorna effettivamente lo stato.
                long ageMs = System.currentTimeMillis() - prefs.getLong("heartbeat_wall", 0);
                if (ageMs >= 0 && ageMs <= 15_000) {
                    SessionTabStats.Tab movementTab = SessionTabStats.tabForMovement(s.movement);
                    if (movementTab != null) {
                        pendingResumeTabSelection = false;
                        if (currentTab != movementTab) {
                            currentTab = movementTab;
                            updateTabButtons();
                        }
                    }
                }
                // Se lo stato è ancora incerto, attendi la prima classificazione.
                // La selezione manuale, invece, resta valida per l'intera apertura.
            }
            if (active) maybeShowGpsAlert(s);
            Rect visiblePart = new Rect();
            boolean mapVisible = map.view().getGlobalVisibleRect(visiblePart)
                    && visiblePart.height() >= Ui.dp(ActiveSessionActivity.this, 48);
            if (mapVisible != mapRendered) {
                mapRendered = mapVisible;
                map.setTrackingMapVisible(mapVisible);
                if (mapVisible) map.onResume();
                else map.onPause();
            }
            if (mapVisible) {
                if (active && s.id > 0) {
                    map.update(db, s.id);
                    map.updateLivePosition(s.lastLatitude, s.lastLongitude, s.lastLocationValid);
                } else {
                    map.clearForSession(0);
                }
            }
            renderTab(s, active);
            gps.setText(active ? s.gpsStatus : "Inizializzazione del GPS…");
            movement.setText(!active ? "Preparazione sessione…" :
                    "RUN".equals(s.movement) ? "🏃 Corsa" :
                    "WALK".equals(s.movement) ? "🚶 Camminata" :
                    "STILL".equals(s.movement) ? "⏸ Fermo" :
                    "MOVING_UNKNOWN".equals(s.movement) ? "↗ Movimento rilevato" : "Analisi del movimento…");
            int interval = AppPrefs.intervalM(prefs);
            checkpoint.setText(RunStats.meters(RunStats.nextCheckpointM(s.distanceM, interval)));
            remaining.setText(RunStats.meters(RunStats.remainingCheckpointM(s.distanceM, interval)));
            int goal = AppPrefs.goalM(prefs);
            goalProgress.setVisibility(goal <= 0 ? View.GONE : View.VISIBLE);
            goalProgress.setProgress(RunStats.goalProgress(s.distanceM, goal));
            goalLabel.setText(goal <= 0 ? "Nessun obiettivo impostato" :
                    "Obiettivo " + RunStats.meters(goal) + " · Mancano " + RunStats.meters(RunStats.goalRemaining(s.distanceM, goal)));
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        pendingResumeTabSelection = true;
        // Render and fetch map tiles only if the card is actually visible.
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }
    @Override protected void onPause() {
        resumed = false;
        pendingResumeTabSelection = false;
        handler.removeCallbacks(refreshTask);
        map.setTrackingMapVisible(false);
        if (mapRendered) {
            map.onPause();
            mapRendered = false;
        }
        super.onPause();
    }
    @Override protected void onDestroy() {
        map.onDestroy();
        db.close();
        super.onDestroy();
    }
}
