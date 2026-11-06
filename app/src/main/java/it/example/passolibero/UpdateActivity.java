package it.example.passolibero;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

/** Controllo, download, verifica e richiesta d'installazione dell'APK firmato. */
public final class UpdateActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView status, version, channelLabel;
    private Button debugButton, stableButton, actionButton;
    private String channel = UpdateManager.CHANNEL_DEBUG;
    private UpdateManager.Offer offer;
    private long downloadId;
    private boolean running, resumed;
    private final AtomicBoolean checking = new AtomicBoolean(false);

    private final Runnable pollDownload = new Runnable() {
        @Override public void run() {
            if (!resumed || downloadId <= 0) return;
            int progress = UpdateManager.downloadStatus(UpdateActivity.this, downloadId);
            if (progress == DownloadManager.STATUS_SUCCESSFUL) {
                actionButton.setText("Verifica APK e installa");
                actionButton.setEnabled(true);
                status.setText("Download terminato. Tocca per verificare firma e integrità.");
                return;
            }
            if (progress == DownloadManager.STATUS_FAILED) {
                clearDownload();
                status.setText("Download non riuscito. Riprova il controllo degli aggiornamenti.");
                return;
            }
            actionButton.setEnabled(false);
            status.setText("Download in corso tramite Android…");
            main.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = AppPrefs.get(this);
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(0, b.top, 0, b.bottom);
            return insets;
        });
        setContentView(root);
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll);
        LinearLayout content = Ui.vertical(this);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 22),
                Ui.dp(this, 18), Ui.dp(this, 30));
        scroll.addView(content);
        Ui.marginBottom(this, content, Ui.text(this, "Aggiornamenti PassoLibero",
                25, true, Ui.INK), 7);
        Ui.marginBottom(this, content, Ui.text(this,
                "Controlla le nuove versioni e installale senza cancellare i dati.",
                14, false, Ui.MUTED), 15);

        LinearLayout card = Ui.card(this);
        version = Ui.text(this, "Versione installata", 16, true, Ui.INK);
        Ui.marginBottom(this, card, version, 9);
        channelLabel = Ui.text(this, "", 13, false, Ui.MUTED);
        Ui.marginBottom(this, card, channelLabel, 12);
        debugButton = Ui.button(this, "Debug · PR #2", false);
        debugButton.setOnClickListener(v -> chooseChannel(UpdateManager.CHANNEL_DEBUG));
        Ui.marginBottom(this, card, debugButton, 7);
        stableButton = Ui.button(this, "Stabile · GitHub Release", false);
        stableButton.setOnClickListener(v -> chooseChannel(UpdateManager.CHANNEL_STABLE));
        Ui.marginBottom(this, card, stableButton, 12);

        Button checkButton = Ui.button(this, "↻ Controlla aggiornamenti", true);
        checkButton.setOnClickListener(v -> check());
        Ui.marginBottom(this, card, checkButton, 11);
        status = Ui.text(this, "Seleziona un canale e controlla gli aggiornamenti.",
                14, false, Ui.INK);
        Ui.marginBottom(this, card, status, 12);
        actionButton = Ui.button(this, "Scarica aggiornamento", true);
        actionButton.setOnClickListener(v -> downloadOrInstall());
        Ui.marginBottom(this, card, actionButton, 5);
        Ui.marginBottom(this, content, card, 12);
        Ui.marginBottom(this, content, Ui.text(this,
                "Le versioni debug della PR #2 sono sperimentali. Il canale stabile "
                        + "sarà disponibile quando verrà pubblicata una release ufficiale. "
                        + "Prima dell'installazione verifichiamo SHA-256, identificativo, "
                        + "versionCode e certificato digitale. Android chiede una conferma "
                        + "per autorizzare l'installazione.",
                13, false, Ui.MUTED), 14);
        Button back = Ui.button(this, "← Torna alla Home", false);
        back.setOnClickListener(v -> finish());
        Ui.marginBottom(this, content, back, 8);
        chooseChannel(UpdateManager.CHANNEL_DEBUG);
    }

    private void chooseChannel(String selected) {
        channel = selected;
        offer = null;
        downloadId = 0;
        version.setText("Versione installata: " + BuildConfig.VERSION_NAME
                + " · build " + BuildConfig.VERSION_CODE
                + (BuildConfig.DEBUG ? " (debug)" : " (release)"));
        debugButton.setEnabled(!UpdateManager.CHANNEL_DEBUG.equals(selected));
        stableButton.setEnabled(!UpdateManager.CHANNEL_STABLE.equals(selected));
        channelLabel.setText("Canale selezionato: " +
                (UpdateManager.CHANNEL_DEBUG.equals(selected) ? "Debug · PR #2" : "Stabile"));
        status.setText("Premi «Controlla aggiornamenti» per verificare le nuove build.");
        actionButton.setEnabled(false);
        restoreDownload();
    }

    private void check() {
        if (!checking.compareAndSet(false, true)) return;
        String requestedChannel = channel;
        status.setText("Controllo degli aggiornamenti da GitHub in corso…");
        actionButton.setEnabled(false);
        new Thread(() -> {
            try {
                UpdateManager.Offer next = UpdateManager.check(
                        requestedChannel, getPackageName(), BuildConfig.VERSION_CODE);
                main.post(() -> {
                    checking.set(false);
                    if (isFinishing() || isDestroyed() || !requestedChannel.equals(channel)) return;
                    offer = next;
                    downloadId = 0;
                    if (next == null) {
                        status.setText("Nessun aggiornamento disponibile per questo canale.");
                        return;
                    }
                    status.setText("Nuova build " + next.versionCode
                            + " disponibile. APK firmato, verifica prima dell'installazione.");
                    actionButton.setText("↓ Scarica build " + next.versionCode);
                    actionButton.setEnabled(!isSessionActive());
                    if (isSessionActive()) status.append(
                            "\nTermina prima la sessione GPS in corso.");
                });
            } catch (Exception ex) {
                main.post(() -> {
                    checking.set(false);
                    if (isFinishing() || isDestroyed() || !requestedChannel.equals(channel)) return;
                    status.setText("Controllo non riuscito: " + ex.getMessage()
                            + "\nVerifica Internet e riprova.");
                });
            }
        }, "PassoLiberoUpdateCheck").start();
    }

    private void downloadOrInstall() {
        if (offer == null) return;
        if (isSessionActive()) {
            status.setText("Termina la sessione GPS prima di aggiornare PassoLibero.");
            return;
        }
        if (downloadId <= 0) {
            try {
                downloadId = UpdateManager.enqueue(this, offer);
                prefs.edit().putLong("update_download_id", downloadId)
                        .putString("update_channel", offer.channel)
                        .putLong("update_code", offer.versionCode)
                        .putString("update_sha256", offer.sha256)
                        .putString("update_cert_sha256", offer.certificateSha256).apply();
                main.removeCallbacks(pollDownload);
                main.post(pollDownload);
            } catch (Exception ex) {
                status.setText("Impossibile scaricare: " + ex.getMessage());
            }
            return;
        }
        if (UpdateManager.downloadStatus(this, downloadId)
                != DownloadManager.STATUS_SUCCESSFUL) {
            main.removeCallbacks(pollDownload);
            main.post(pollDownload);
            return;
        }
        if (running) return;
        running = true;
        actionButton.setEnabled(false);
        status.setText("Verifica dell'APK e della firma digitale in corso…");
        UpdateManager.Offer verifying = offer;
        long verifyingId = downloadId;
        new Thread(() -> {
            try {
                UpdateManager.verify(getApplicationContext(), verifying);
                main.post(() -> {
                    running = false;
                    if (isFinishing() || isDestroyed()) return;
                    if (isSessionActive()) {
                        status.setText("Sessione attiva: aggiorna dopo la fine dell'allenamento.");
                        return;
                    }
                    if (!UpdateManager.canInstall(this)) {
                        status.setText("Autorizza l'installazione da PassoLibero nelle "
                                + "impostazioni Android, poi premi nuovamente «Verifica APK e installa».");
                        actionButton.setEnabled(true);
                        startActivity(UpdateManager.installPermissionIntent(this));
                        return;
                    }
                    try {
                        startActivity(UpdateManager.installIntent(this, verifyingId));
                    } catch (Exception ex) {
                        actionButton.setEnabled(true);
                        status.setText("Installazione non avviata: " + ex.getMessage());
                    }
                });
            } catch (Exception ex) {
                main.post(() -> {
                    running = false;
                    if (isFinishing() || isDestroyed()) return;
                    clearDownload();
                    status.setText("APK rifiutato: " + ex.getMessage()
                            + "\nScarica un nuovo APK dopo aver controllato gli aggiornamenti.");
                });
            }
        }, "PassoLiberoVerifyUpdate").start();
    }

    private boolean isSessionActive() {
        return prefs.getBoolean("active", false);
    }

    private void restoreDownload() {
        long id = prefs.getLong("update_download_id", 0);
        if (id <= 0 || !channel.equals(prefs.getString("update_channel", ""))) return;
        long code = prefs.getLong("update_code", 0);
        String sha = prefs.getString("update_sha256", "");
        String cert = prefs.getString("update_cert_sha256", "");
        if (!UpdatePolicy.newer(BuildConfig.VERSION_CODE, code)
                || !UpdatePolicy.validSha256(sha) || !UpdatePolicy.validSha256(cert)) {
            clearDownload();
            return;
        }
        try {
            String tag = UpdateManager.CHANNEL_DEBUG.equals(channel) ?
                    UpdatePolicy.DEBUG_TAG : "stable";
            offer = new UpdateManager.Offer(channel, tag,
                    UpdatePolicy.assetName(channel, code), sha, cert, code, -1);
            downloadId = id;
            main.post(pollDownload);
        } catch (IllegalArgumentException ex) {
            clearDownload();
        }
    }

    private void clearDownload() {
        downloadId = 0;
        prefs.edit().remove("update_download_id").remove("update_channel")
                .remove("update_code").remove("update_sha256")
                .remove("update_cert_sha256").apply();
        actionButton.setEnabled(false);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        main.removeCallbacks(pollDownload);
        if (downloadId > 0) main.post(pollDownload);
    }

    @Override protected void onPause() {
        resumed = false;
        main.removeCallbacks(pollDownload);
        super.onPause();
    }
}
