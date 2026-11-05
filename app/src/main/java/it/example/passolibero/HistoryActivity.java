package it.example.passolibero;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Elenco offline: scegli una sessione per visualizzare TUTTI i suoi dati e la traccia. */
public final class HistoryActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
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
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 28));
        scroll.addView(content);
        Ui.marginBottom(this, content, Ui.text(this, "Storico allenamenti", 28, true, Ui.INK), 5);
        Ui.marginBottom(this, content, Ui.text(this,
                "Tutte le sessioni conservate sul telefono. Tocca una voce per i dettagli.",
                13, false, Ui.MUTED), 18);
        TrackerDb db = new TrackerDb(this);
        List<TrackerDb.Entry> entries = db.recent(200);
        db.close();
        if (entries.isEmpty()) {
            Ui.marginBottom(this, content, Ui.text(this, "Non hai ancora sessioni salvate.",
                    16, false, Ui.MUTED), 6);
        }
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (TrackerDb.Entry s : entries) {
            LinearLayout card = Ui.card(this);
            Ui.marginBottom(this, card,
                    Ui.text(this, df.format(new Date(s.startedAt)), 16, true, Ui.INK), 6);
            Ui.marginBottom(this, card, Ui.text(this,
                    RunMath.km(s.distance) + " km  ·  " + RunMath.duration(s.elapsedMs),
                    20, true, Ui.GREEN), 5);
            Ui.marginBottom(this, card, Ui.text(this,
                    "Ritmo " + RunMath.pace(s.elapsedMs, s.distance) + " min/km"
                            + "  ·  " + s.steps + " passi", 13, false, Ui.MUTED), 5);
            card.addView(Ui.text(this, "COMPLETED".equals(s.status) ?
                    "Completata · Tocca per i dettagli" : "In corso/interrotta · Tocca per i dettagli",
                    12, false, Ui.MUTED));
            card.setOnClickListener(v -> {
                Intent i = new Intent(this, SessionDetailActivity.class);
                i.putExtra("session_id", s.id);
                startActivity(i);
            });
            Ui.marginBottom(this, content, card, 10);
        }
    }
}
