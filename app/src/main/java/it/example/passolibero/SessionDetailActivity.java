package it.example.passolibero;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * La stessa struttura della sessione corrente, ma alimentata esclusivamente
 * dai dati salvati e senza i comandi che modificano un allenamento in corso.
 */
public final class SessionDetailActivity extends Activity {
    private TrackMap map;
    private TrackerDb db;
    private TrackerDb.Entry session;
    private long sessionId;
    private SessionTabStats.Tab selectedTab = SessionTabStats.Tab.TOTAL;
    private Button[] tabButtons;
    private LinearLayout runningSpeedBox, sprintCard, statsCard;
    private TextView tabTitle, distance, duration, paceAvg, averageSpeed,
            currentPace, currentSpeed, steps, cadence, maxRunSpeed, tabHint, unclassified;
    private List<TrackerDb.SprintRecord> sprintRecords;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        sessionId = getIntent().getLongExtra("session_id", -1);
        db = new TrackerDb(this);
        session = db.session(sessionId);
        if (session == null) { finish(); return; }
        if (state != null) {
            int tabIndex = state.getInt("stats_tab", SessionTabStats.Tab.TOTAL.ordinal());
            if (tabIndex >= 0 && tabIndex < SessionTabStats.Tab.values().length)
                selectedTab = SessionTabStats.Tab.values()[tabIndex];
        }
        sprintRecords = db.sprints(sessionId);
        buildUi();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("stats_tab", selectedTab.ordinal());
        super.onSaveInstanceState(state);
    }

    private TextView stat(LinearLayout parent, String label, String initial) {
        Ui.marginBottom(this, parent, Ui.text(this, label, 11, true, Ui.MUTED), 1);
        TextView value = Ui.text(this, initial, 21, true, Ui.INK);
        Ui.marginBottom(this, parent, value, 12);
        return value;
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
        root.addView(scroll);
        LinearLayout content = Ui.vertical(this);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 18),
                Ui.dp(this, 18), Ui.dp(this, 25));
        scroll.addView(content);

        Ui.marginBottom(this, content,
                Ui.text(this, "Sessione salvata", 27, true, Ui.INK), 5);
        String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(session.startedAt));
        Ui.marginBottom(this, content, Ui.text(this, date, 16, true, Ui.GREEN), 3);
        Ui.marginBottom(this, content, Ui.text(this,
                "COMPLETED".equals(session.status) ? "✓ Allenamento completato"
                        : "Allenamento interrotto o non completato",
                13, false, Ui.MUTED), 14);

        // Stesse quattro tab e stesse proiezioni della schermata live.
        LinearLayout tabBar = Ui.horizontal(this);
        String[] labels = {"🏃 Corsa", "🚶 Cammina", "∑ Totali", "⚡ Sprint"};
        SessionTabStats.Tab[] tabs = SessionTabStats.Tab.values();
        tabButtons = new Button[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            final SessionTabStats.Tab tab = tabs[i];
            Button button = Ui.button(this, labels[i], false);
            button.setTextSize(11);
            button.setContentDescription("Statistiche storiche " + labels[i]);
            button.setOnClickListener(v -> {
                selectedTab = tab;
                updateTabButtons();
                renderTab();
            });
            tabButtons[i] = button;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, 50), 1f);
            params.rightMargin = Ui.dp(this, i == tabs.length - 1 ? 0 : 3);
            tabBar.addView(button, params);
        }
        Ui.marginBottom(this, content, tabBar, 12);

        statsCard = Ui.card(this);
        tabTitle = Ui.text(this, "", 14, true, Ui.MUTED);
        Ui.marginBottom(this, statsCard, tabTitle, 4);
        distance = Ui.text(this, "0,00 km", 43, true, Ui.INK);
        Ui.marginBottom(this, statsCard, distance, 12);
        duration = stat(statsCard, "TEMPO", "00:00");
        paceAvg = stat(statsCard, "RITMO MEDIO (min/km)", "--:-- min/km");
        averageSpeed = stat(statsCard, "VELOCITÀ MEDIA", "-- km/h");
        runningSpeedBox = Ui.vertical(this);
        maxRunSpeed = stat(runningSpeedBox, "VELOCITÀ MASSIMA CORSA", "-- km/h");
        Ui.marginBottom(this, statsCard, runningSpeedBox, 0);
        currentPace = stat(statsCard, "RITMO ATTUALE (min/km)", "--:-- min/km");
        currentSpeed = stat(statsCard, "VELOCITÀ ATTUALE", "-- km/h");
        steps = stat(statsCard, "PASSI ATTRIBUITI", "—");
        cadence = stat(statsCard, "CADENZA MEDIA", "-- passi/min");
        tabHint = Ui.text(this, "", 13, false, Ui.MUTED);
        Ui.marginBottom(this, statsCard, tabHint, 8);
        unclassified = Ui.text(this, "", 12, false, Ui.MUTED);
        Ui.marginBottom(this, statsCard, unclassified, 0);
        Ui.marginBottom(this, content, statsCard, 12);

        sprintCard = Ui.card(this);
        Ui.marginBottom(this, sprintCard,
                Ui.text(this, "🏃 MODALITÀ SPRINT", 17, true, Ui.INK), 7);
        Ui.marginBottom(this, sprintCard, Ui.text(this,
                "Riepilogo delle prove sprint registrate durante l'allenamento. "
                        + "I controlli per avviare nuovi sprint sono disponibili solo "
                        + "nella sessione corrente.",
                13, false, Ui.MUTED), 8);
        if (sprintRecords.isEmpty()) {
            Ui.marginBottom(this, sprintCard, Ui.text(this,
                    "Nessuno sprint registrato oppure dati non disponibili per questa sessione.",
                    14, false, Ui.MUTED), 3);
        }
        for (int i = 0; i < sprintRecords.size(); i++) {
            TrackerDb.SprintRecord sp = sprintRecords.get(i);
            Ui.marginBottom(this, sprintCard, Ui.text(this,
                    "SPRINT " + (i + 1) + " · "
                            + ("DISTANCE".equals(sp.mode) ? sp.target + " m" : sp.target + " s")
                            + " · Countdown " + sp.countdownSeconds + " s",
                    14, true, Ui.GREEN), 4);
            String outcome = "CANCELLED".equals(sp.outcome) ? "Annullato"
                    : "LIMITE_TEMPO".equals(sp.outcome) ? "Terminato per limite di tempo"
                    : "Concluso";
            String speedMax = sp.peakKmh == null ? "-- km/h"
                    : RunMath.velocity(sp.peakKmh) + " km/h";
            String speedAvg = sp.averageKmh == null ? "-- km/h"
                    : RunMath.velocity(sp.averageKmh) + " km/h";
            Ui.marginBottom(this, sprintCard, Ui.text(this,
                    outcome + " · " + String.format(Locale.ITALY, "%.1f m", sp.distanceM)
                            + " · " + RunMath.duration(sp.elapsedMs)
                            + "\nVelocità media " + speedAvg
                            + " · velocità massima " + speedMax
                            + "\nCampioni GNSS validi: " + sp.samples,
                    13, false, Ui.INK), 10);
        }
        Ui.marginBottom(this, content, sprintCard, 12);

        LinearLayout mapCard = Ui.card(this);
        Ui.marginBottom(this, mapCard, Ui.text(this, "PERCORSO GPS · MAPPA",
                14, true, Ui.INK), 8);
        map = new TrackMap(this);
        map.setAutoFollowCurrentPosition(false);
        mapCard.addView(map.view(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 300)));
        Ui.marginBottom(this, mapCard, Ui.text(this,
                "Tocca un tratto della mappa per vedere le statistiche della sezione.",
                12, false, Ui.MUTED), 5);
        Ui.marginBottom(this, mapCard, Ui.text(this,
                "🟢 Camminata · 🔵 Corsa · 🔴 Sprint · ⚪ Fermo/incerto",
                12, true, Ui.INK), 7);
        Ui.marginBottom(this, mapCard, Ui.text(this,
                "© OpenStreetMap contributors · Percorso salvato, esplorabile con le dita",
                11, false, Ui.MUTED), 0);
        Ui.marginBottom(this, content, mapCard, 12);
        map.clearForSession(sessionId);
        for (int batch = 0; batch < 100; batch++) {
            if (map.update(db, sessionId) < 500) break;
        }
        map.view().post(() -> map.zoomToRoute());

        LinearLayout progressCard = Ui.card(this);
        Ui.marginBottom(this, progressCard, Ui.text(this, "CHECKPOINT E OBIETTIVO",
                14, true, Ui.INK), 6);
        if (session.checkpointIntervalM != null && session.checkpointIntervalM > 0) {
            int interval = session.checkpointIntervalM;
            int reached = (int) Math.floor(Math.max(0, session.distance) / interval);
            stat(progressCard, "INTERVALLO CHECKPOINT", RunStats.meters(interval));
            stat(progressCard, "ULTIMO CHECKPOINT RAGGIUNTO",
                    reached <= 0 ? "Nessuno" : RunStats.meters((double) reached * interval));
        } else {
            stat(progressCard, "INTERVALLO CHECKPOINT", "— (dato storico non disponibile)");
        }
        TextView goal = Ui.text(this, session.goalM > 0
                ? "Obiettivo " + RunStats.meters(session.goalM)
                  + " · Rimanenti alla fine " + RunStats.meters(
                        RunStats.goalRemaining(session.distance, session.goalM))
                : "Nessun obiettivo impostato", 14, true, Ui.GREEN);
        Ui.marginBottom(this, progressCard, goal, 7);
        if (session.goalM > 0) {
            ProgressBar bar = new ProgressBar(this, null,
                    android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(RunStats.goalProgress(session.distance, session.goalM));
            Ui.marginBottom(this, progressCard, bar, 9);
        }
        Ui.marginBottom(this, content, progressCard, 12);

        LinearLayout totalsCard = Ui.card(this);
        Ui.marginBottom(this, totalsCard, Ui.text(this,
                "DETTAGLIO COMPLETO DELLA SESSIONE", 14, true, Ui.INK), 8);
        stat(totalsCard, "DURATA COMPLESSIVA", RunMath.duration(session.elapsedMs));
        stat(totalsCard, "DISTANZA COMPLESSIVA", RunMath.km(session.distance) + " km");
        stat(totalsCard, "PASSI TOTALI", Integer.toString(session.steps));
        stat(totalsCard, "TEMPO IN MOVIMENTO", RunMath.duration(session.movingMs));
        stat(totalsCard, "TEMPO DI CORSA", RunMath.duration(session.runMs));
        stat(totalsCard, "TEMPO DI CAMMINATA", RunMath.duration(session.walkMs));
        stat(totalsCard, "TEMPO DA FERMO", RunMath.duration(session.stillMs));
        stat(totalsCard, "MOVIMENTO NON CLASSIFICATO",
                RunMath.duration(session.movingUnknownMs));
        stat(totalsCard, "MOVIMENTO INCERTO", RunMath.duration(session.unknownMs));
        Ui.marginBottom(this, content, totalsCard, 12);

        Button export = Ui.button(this, "↓ Esporta sessione in JSON", false);
        export.setOnClickListener(v -> SessionJsonExporter.requestSave(this, sessionId));
        Ui.marginBottom(this, content, export, 6);
        Ui.marginBottom(this, content, Ui.text(this,
                "Il JSON include anche il tracciato GPS con coordinate precise.",
                12, false, Ui.MUTED), 12);
        Button back = Ui.button(this, "← Torna allo storico", false);
        back.setOnClickListener(v -> finish());
        Ui.marginBottom(this, content, back, 6);

        updateTabButtons();
        renderTab();
    }

    private void updateTabButtons() {
        if (tabButtons == null) return;
        SessionTabStats.Tab[] tabs = SessionTabStats.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = tabs[i] == selectedTab;
            tabButtons[i].setSelected(selected);
            tabButtons[i].setTextColor(selected ? Color.WHITE : Ui.GREEN);
            tabButtons[i].setBackground(Ui.shape(selected ? Ui.GREEN
                    : Color.rgb(230, 246, 242), 12, this));
        }
    }

    private void renderTab() {
        SessionTabStats.Tab tab = selectedTab;
        boolean sprint = tab == SessionTabStats.Tab.SPRINT;
        statsCard.setVisibility(sprint ? View.GONE : View.VISIBLE);
        sprintCard.setVisibility(sprint ? View.VISIBLE : View.GONE);
        if (sprint) return;
        boolean running = tab == SessionTabStats.Tab.RUN;
        runningSpeedBox.setVisibility(running ? View.VISIBLE : View.GONE);
        tabTitle.setText(running ? "SOLO CORSA" :
                tab == SessionTabStats.Tab.WALK ? "SOLO CAMMINATA"
                        : "CORSA + CAMMINATA");
        double meters = SessionTabStats.meters(tab, session.runM, session.walkM);
        long milliseconds = SessionTabStats.milliseconds(tab, session.runMs, session.walkMs);
        distance.setText(RunMath.km(meters) + " km");
        duration.setText(RunMath.duration(milliseconds));
        paceAvg.setText(RunMath.pace(milliseconds, meters) + " min/km");
        double avg = SessionTabStats.averageSpeedKmh(milliseconds, meters);
        averageSpeed.setText(Double.isFinite(avg)
                ? RunMath.velocity(avg) + " km/h" : "-- km/h");
        maxRunSpeed.setText(session.maxRunSpeedKmh != null
                && Double.isFinite(session.maxRunSpeedKmh) && session.maxRunSpeedKmh > 0
                ? RunMath.velocity(session.maxRunSpeedKmh) + " km/h" : "-- km/h");
        // I valori istantanei non sono registrati come snapshot finale:
        // mostrarli come zero sarebbe scorretto.
        currentPace.setText("--:-- min/km");
        currentSpeed.setText("-- km/h");
        Integer tabSteps = null;
        if (session.runSteps != null && session.walkSteps != null) {
            tabSteps = SessionTabStats.steps(tab, session.runSteps, session.walkSteps);
        } else if (tab == SessionTabStats.Tab.RUN && session.runSteps != null) {
            tabSteps = session.runSteps;
        } else if (tab == SessionTabStats.Tab.WALK && session.walkSteps != null) {
            tabSteps = session.walkSteps;
        }
        steps.setText(tabSteps == null ? "—" : Integer.toString(tabSteps));
        double avgCadence = tabSteps == null ? Double.NaN
                : SessionTabStats.averageCadence(milliseconds, tabSteps);
        cadence.setText(Double.isFinite(avgCadence)
                ? Math.round(avgCadence) + " passi/min" : "-- passi/min");
        tabHint.setText(running
                ? "Dati attribuiti esclusivamente ai tratti classificati come corsa."
                : tab == SessionTabStats.Tab.WALK
                ? "Dati attribuiti esclusivamente ai tratti classificati come camminata."
                : "Somma dei tratti classificati come corsa e camminata, senza periodi incerti.");
        if (tab == SessionTabStats.Tab.TOTAL) {
            String count = session.runSteps == null || session.walkSteps == null
                    ? "non disponibili"
                    : Integer.toString(Math.max(0, session.steps
                            - session.runSteps - session.walkSteps));
            unclassified.setText("Fuori dalle due categorie: " + count + " passi, "
                    + RunStats.meters(Math.max(0,
                            session.distance - session.runM - session.walkM))
                    + "; fermo " + RunMath.duration(session.stillMs)
                    + ", movimento non classificato "
                    + RunMath.duration(session.movingUnknownMs)
                    + ", incerto " + RunMath.duration(session.unknownMs)
                    + ". Totale sessione: " + RunMath.duration(session.elapsedMs) + ".");
            unclassified.setVisibility(View.VISIBLE);
        } else {
            unclassified.setVisibility(View.GONE);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SessionJsonExporter.REQUEST_CODE) {
            SessionJsonExporter.onSaveResult(this, sessionId, resultCode, data);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override protected void onPause() {
        if (map != null) map.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (map != null) map.onDestroy();
        if (db != null) db.close();
        super.onDestroy();
    }
}
