package it.example.passolibero;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import it.example.passolibero.application.history.SessionReadPort;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Storico offline: dettaglio e cancellazione mirata, con conferma dell'utente. */
public final class HistoryActivity extends Activity {
    private LinearLayout list;
    private final Set<Long> deleting = new HashSet<>();

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
                "Tocca una sessione per visualizzare i dettagli o usa Elimina per cancellarla.",
                13, false, Ui.MUTED), 15);
        list = Ui.vertical(this);
        Ui.marginBottom(this, content, list, 4);
    }

    @Override protected void onResume() {
        super.onResume();
        // Aggiorna l'elenco anche quando si torna dai dettagli.
        showSessions();
    }

    private void showSessions() {
        if (list == null) return;
        list.removeAllViews();
        List<SessionReadPort.SessionRow> entries;
        try {
            entries = HistoryDataBridge.listRecent(this);
        } catch (RuntimeException error) {
            android.util.Log.e("PassoLiberoHistory", "Lettura dello storico non riuscita", error);
            Ui.marginBottom(this, list, Ui.text(this,
                    "Impossibile caricare lo storico. Riprova ad aprire la schermata.",
                    14, false, Ui.MUTED), 8);
            return;
        }
        if (entries.isEmpty()) {
            Ui.marginBottom(this, list, Ui.text(this, "Non hai ancora sessioni salvate.",
                    16, false, Ui.MUTED), 6);
            return;
        }
        DateFormat dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        boolean active = AppPrefs.get(this).getBoolean("active", false);
        long activeId = AppPrefs.get(this).getLong("active_id", 0);
        for (SessionReadPort.SessionRow session : entries) {
            LinearLayout card = Ui.card(this);
            String date = dates.format(new Date(session.startedAt));
            Ui.marginBottom(this, card, Ui.text(this, date, 16, true, Ui.INK), 6);
            Ui.marginBottom(this, card, Ui.text(this,
                    RunMath.km(session.distance) + " km  ·  " + RunMath.duration(session.elapsedMs),
                    20, true, Ui.GREEN), 5);
            Ui.marginBottom(this, card, Ui.text(this,
                    "Ritmo " + RunMath.pace(session.elapsedMs, session.distance) + " min/km"
                            + "  ·  " + session.steps + " passi", 13, false, Ui.MUTED), 6);
            Ui.marginBottom(this, card, Ui.text(this,
                    "COMPLETED".equals(session.status) ? "Completata"
                            : "In corso/interrotta", 12, false, Ui.MUTED), 8);

            Button details = Ui.button(this, "Apri dettagli e percorso GPS", false);
            details.setOnClickListener(v -> {
                Intent intent = new Intent(this, SessionDetailActivity.class);
                intent.putExtra("session_id", session.id);
                startActivity(intent);
            });
            Ui.marginBottom(this, card, details, 7);
            Button remove = Ui.button(this, "Elimina sessione", false);
            boolean live = active && session.id == activeId;
            remove.setEnabled(!live && !deleting.contains(session.id));
            remove.setContentDescription("Elimina la sessione del " + date);
            if (live) {
                remove.setText("Sessione attiva: non eliminabile");
            }
            remove.setOnClickListener(v -> confirmDelete(session.id, date));
            Ui.marginBottom(this, card, remove, 0);
            Ui.marginBottom(this, list, card, 10);
        }
    }

    private void confirmDelete(long sessionId, String date) {
        if (deleting.contains(sessionId)) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Eliminare la sessione?")
                .setMessage("Sessione del " + date + ". Verranno cancellati definitivamente "
                        + "statistiche e tutti i punti del percorso GPS. "
                        + "Questa operazione non si può annullare. "
                        + "Per conservare i dati, esportali prima in JSON dai dettagli.")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Elimina", (dialog, which) -> deleteSession(sessionId))
                .show();
    }

    private void deleteSession(long sessionId) {
        if (!deleting.add(sessionId)) return;
        showSessions(); // Disabilita subito il tasto della sessione selezionata.
        new Thread(() -> {
            boolean deleted = false;
            try (TrackerDb db = new TrackerDb(getApplicationContext())) {
                deleted = db.deleteSession(sessionId);
            } catch (RuntimeException error) {
                android.util.Log.e("PassoLiberoHistory", "Eliminazione sessione fallita", error);
            }
            final boolean success = deleted;
            runOnUiThread(() -> {
                deleting.remove(sessionId);
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, success ? "Sessione e percorso GPS eliminati"
                        : "Impossibile eliminare. Se la sessione è attiva, terminala prima.",
                        Toast.LENGTH_LONG).show();
                showSessions();
            });
        }, "DeleteHistorySession").start();
    }
}
