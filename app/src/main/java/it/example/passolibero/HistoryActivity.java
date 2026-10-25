package it.example.passolibero;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Cronologia salvata offline, comprese sessioni rimaste interrotte. */
public final class HistoryActivity extends Activity {
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom); return insets;
        });
        setContentView(root);
        ScrollView scroll = new ScrollView(this); root.addView(scroll);
        LinearLayout content = Ui.vertical(this);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24));
        scroll.addView(content);
        TextView title = Ui.text(this, "Le tue sessioni", 28, true, Ui.INK);
        Ui.marginBottom(this, content, title, 6);
        Ui.marginBottom(this, content, Ui.text(this,
                "Storico locale sul dispositivo", 13, false, Ui.MUTED), 20);
        TrackerDb helper = new TrackerDb(this);
        List<TrackerDb.Entry> history = helper.recent(50);
        helper.close();
        if (history.isEmpty()) {
            Ui.marginBottom(this, content, Ui.text(this,
                    "Nessuna sessione registrata. Torna indietro e inizia una corsa!", 16, false, Ui.MUTED), 8);
        }
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (TrackerDb.Entry s : history) {
            LinearLayout card = Ui.card(this);
            Ui.marginBottom(this, card, Ui.text(this,
                    format.format(new Date(s.startedAt)), 15, true, Ui.INK), 8);
            Ui.marginBottom(this, card, Ui.text(this,
                    RunMath.km(s.distance) + " km  •  " + RunMath.duration(s.elapsedMs)
                            + "  •  " + s.steps + " passi", 16, true, Ui.GREEN), 6);
            Ui.marginBottom(this, card, Ui.text(this,
                    "Ritmo: " + RunMath.pace(s.elapsedMs, s.distance) + "/km"
                            + "    Corsa: " + RunMath.km(s.runM) + " km"
                            + "    Camminata: " + RunMath.km(s.walkM) + " km", 12, false, Ui.MUTED), 4);
            card.addView(Ui.text(this, "COMPLETED".equals(s.status) ? "Completata" : "Interrotta o in corso",
                    12, false, Ui.MUTED));
            Ui.marginBottom(this, content, card, 12);
        }
    }
}
