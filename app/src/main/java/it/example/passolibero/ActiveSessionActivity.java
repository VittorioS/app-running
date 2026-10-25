package it.example.passolibero;

import android.app.Activity;
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
    private TextView distance, paceNow, paceAvg, duration, movingTime, speed, steps,
            cadence, runTime, walkTime, stillTime, runDistance, walkDistance,
            movement, gps, checkpoint, remaining, goalLabel;
    private ProgressBar goalProgress;
    private boolean resumed, sessionSeen;

    private static final int[] CHECKPOINTS = {250, 500, 1000, 2000, 5000};
    private static final int[] GOALS = {0, 1000, 2000, 3000, 5000, 10000, 21097, -1};

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        prefs = AppPrefs.get(this);
        db = new TrackerDb(this);
        buildUi();
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

        LinearLayout primary = Ui.card(this);
        Ui.marginBottom(this, primary, Ui.text(this, "DISTANZA TOTALE", 12, true, Ui.MUTED), 3);
        distance = Ui.text(this, "0,00 km", 44, true, Ui.INK);
        Ui.marginBottom(this, primary, distance, 13);
        paceNow = stat(primary, "RITMO ATTUALE (min/km)", "--:-- min/km");
        paceAvg = stat(primary, "RITMO MEDIO (min/km)", "--:-- min/km");
        duration = stat(primary, "TEMPO TOTALE", "00:00");
        Ui.marginBottom(this, content, primary, 12);

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

        LinearLayout metrics = Ui.card(this);
        Ui.marginBottom(this, metrics, Ui.text(this, "DATI DI MOVIMENTO", 14, true, Ui.INK), 12);
        speed = stat(metrics, "VELOCITÀ ATTUALE", "0,0 km/h");
        movingTime = stat(metrics, "TEMPO IN MOVIMENTO", "00:00");
        steps = stat(metrics, "PASSI", "0");
        cadence = stat(metrics, "CADENZA", "-- passi/min");
        runTime = stat(metrics, "TEMPO DI CORSA", "00:00");
        walkTime = stat(metrics, "TEMPO DI CAMMINATA", "00:00");
        stillTime = stat(metrics, "TEMPO DA FERMO", "00:00");
        runDistance = stat(metrics, "DISTANZA CORSA", "0,00 km");
        walkDistance = stat(metrics, "DISTANZA CAMMINATA", "0,00 km");
        Ui.marginBottom(this, content, metrics, 12);

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

        LinearLayout train = Ui.card(this);
        Ui.marginBottom(this, train, Ui.text(this, "Insegna all'app il movimento attuale", 14, true, Ui.INK), 10);
        Button walk = Ui.button(this, "Sto camminando", false);
        walk.setOnClickListener(v -> label(TrackingService.LABEL_WALK));
        Ui.marginBottom(this, train, walk, 6);
        Button run = Ui.button(this, "Sto correndo", false);
        run.setOnClickListener(v -> label(TrackingService.LABEL_RUN));
        Ui.marginBottom(this, train, run, 0);
        Ui.marginBottom(this, content, train, 12);

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
            if (active && s.id > 0) {
                map.update(db, s.id);
                map.updateLivePosition(s.lastLatitude, s.lastLongitude, s.lastLocationValid);
            } else {
                map.clearForSession(0);
            }
            long elapsed = active ? Math.max(0, SystemClock.elapsedRealtime() - s.startedElapsedMs) : 0;
            distance.setText(RunMath.km(active ? s.distanceM : 0) + " km");
            paceNow.setText(RunStats.instantPace(active ? s.speedKmh : 0) + " min/km");
            paceAvg.setText(RunMath.pace(elapsed, s.distanceM) + " min/km");
            duration.setText(RunMath.duration(elapsed));
            speed.setText(RunMath.velocity(active ? s.speedKmh : 0) + " km/h");
            movingTime.setText(RunMath.duration(active ? s.movingMs : 0));
            runTime.setText(RunMath.duration(active ? s.runMs : 0));
            walkTime.setText(RunMath.duration(active ? s.walkMs : 0));
            stillTime.setText(RunMath.duration(active ? s.stillMs : 0));
            steps.setText(Integer.toString(active ? s.steps : 0));
            cadence.setText(active && s.cadenceSpm > 0 ? s.cadenceSpm + " passi/min" : "-- passi/min");
            runDistance.setText(RunMath.km(active ? s.runM : 0) + " km");
            walkDistance.setText(RunMath.km(active ? s.walkM : 0) + " km");
            gps.setText(active ? s.gpsStatus : "Inizializzazione del GPS…");
            movement.setText(!active ? "Preparazione sessione…" :
                    "RUN".equals(s.movement) ? "🏃 Corsa" :
                    "WALK".equals(s.movement) ? "🚶 Camminata" :
                    "STILL".equals(s.movement) ? "⏸ Fermo" : "Analisi del movimento…");
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
        map.onResume();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }
    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refreshTask);
        map.onPause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        map.onDestroy();
        db.close();
        super.onDestroy();
    }
}
