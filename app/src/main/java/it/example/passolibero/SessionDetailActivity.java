package it.example.passolibero;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

/** Dettaglio di una singola sessione storica con la sua traccia GPS. */
public final class SessionDetailActivity extends Activity {
    private TrackMap map;
    private TrackerDb db;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        long id = getIntent().getLongExtra("session_id", -1);
        db = new TrackerDb(this);
        TrackerDb.Entry session = db.session(id);
        if (session == null) { finish(); return; }

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
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 28));
        scroll.addView(content);

        Ui.marginBottom(this, content, Ui.text(this, "Dettaglio allenamento", 25, true, Ui.INK), 5);
        Ui.marginBottom(this, content, Ui.text(this,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(session.startedAt)), 14, false, Ui.MUTED), 12);

        map = new TrackMap(this);
        content.addView(map.view(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 290)));
        Ui.marginBottom(this, content, Ui.text(this,
                "© OpenStreetMap contributors · Percorso registrato offline", 11, false, Ui.MUTED), 10);
        // Disegna solo i punti collegati all'ID selezionato (mai le altre sessioni).
        map.clearForSession(id);
        for (int batch = 0; batch < 100; batch++) {
            if (map.update(db, id) < 500) break;
        }
        map.view().post(() -> map.zoomToRoute());

        LinearLayout card = Ui.card(this);
        add(card, "DISTANZA TOTALE", RunMath.km(session.distance) + " km");
        add(card, "DURATA TOTALE", RunMath.duration(session.elapsedMs));
        add(card, "RITMO MEDIO", RunMath.pace(session.elapsedMs, session.distance) + " min/km");
        add(card, "VELOCITÀ MEDIA TOTALE", session.elapsedMs > 0
                ? RunMath.velocity(session.distance * 3.6 / (session.elapsedMs / 1000.0)) + " km/h" : "--");
        add(card, "TEMPO IN MOVIMENTO", RunMath.duration(session.movingMs));
        add(card, "TEMPO DI CORSA", RunMath.duration(session.runMs));
        add(card, "TEMPO DI CAMMINATA", RunMath.duration(session.walkMs));
        add(card, "TEMPO DA FERMO", RunMath.duration(session.stillMs));
        add(card, "DISTANZA CORSA", RunMath.km(session.runM) + " km");
        add(card, "DISTANZA CAMMINATA", RunMath.km(session.walkM) + " km");
        add(card, "PASSI TOTALI", Integer.toString(session.steps));
        if (session.goalM > 0) add(card, "OBIETTIVO", RunStats.meters(session.goalM));
        add(card, "STATO", "COMPLETED".equals(session.status) ? "Completata" : "Interrotta o ancora attiva");
        Ui.marginBottom(this, content, card, 12);
    }

    private void add(LinearLayout card, String label, String value) {
        Ui.marginBottom(this, card, Ui.text(this, label, 11, true, Ui.MUTED), 2);
        Ui.marginBottom(this, card, Ui.text(this, value, 20, true, Ui.INK), 13);
    }

    @Override protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override protected void onPause() { if (map != null) map.onPause(); super.onPause(); }
    @Override protected void onDestroy() {
        if (map != null) map.onDestroy();
        if (db != null) db.close();
        super.onDestroy();
    }
}
