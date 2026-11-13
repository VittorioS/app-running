package it.example.passolibero;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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
    private Switch autoToggle;

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
        autoToggle = new Switch(this);
        autoToggle.setText("Acquisizione automatica: dopo 1 min, per 10 min");
        autoToggle.setChecked(AppPrefs.get(this).getBoolean("battery_auto_debug", false));
        autoToggle.setOnCheckedChangeListener((v, enabled) -> {
            AppPrefs.get(this).edit().putBoolean("battery_auto_debug", enabled).apply();
            if (!enabled) BatteryAutoCapture.disable(this);
        });
        Ui.marginBottom(this, card, autoToggle, 14);

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
            if (!BatteryRecorder.hasReport(getApplicationContext())) return;
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
        status.setText(BatteryRecorder.status(getApplicationContext()));
        start.setEnabled(!recording);
        stop.setEnabled(recording);
        export.setEnabled(!recording && BatteryRecorder.hasReport(getApplicationContext()));
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
        String csv = BatteryRecorder.csv(getApplicationContext());
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
