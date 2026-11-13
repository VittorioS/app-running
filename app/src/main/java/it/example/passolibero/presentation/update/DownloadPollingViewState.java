package it.example.passolibero.presentation.update;

import it.example.passolibero.application.update.ReadDownloadState;

/**
 * Pure UI representation of the three existing Android download-polling branches.
 *
 * <p>READY_TO_VERIFY enables the verification button, not the installer. This class does not
 * download, verify, install or persist anything.
 */
public final class DownloadPollingViewState {
    public final String actionLabel;
    public final boolean actionEnabled;
    public final String statusText;
    public final boolean clearSavedDownload;
    public final long nextPollDelayMillis;

    private DownloadPollingViewState(
            String actionLabel,
            boolean actionEnabled,
            String statusText,
            boolean clearSavedDownload,
            long nextPollDelayMillis) {
        this.actionLabel = actionLabel;
        this.actionEnabled = actionEnabled;
        this.statusText = statusText;
        this.clearSavedDownload = clearSavedDownload;
        this.nextPollDelayMillis = nextPollDelayMillis;
    }

    public static DownloadPollingViewState from(ReadDownloadState.State status) {
        if (status == ReadDownloadState.State.READY_TO_VERIFY) {
            return new DownloadPollingViewState(
                    "Verifica APK e installa",
                    true,
                    "Download terminato. Tocca per verificare firma e integrità.",
                    false,
                    0L);
        }
        if (status == ReadDownloadState.State.FAILED) {
            return new DownloadPollingViewState(
                    null,
                    false,
                    "Download non riuscito. Riprova il controllo degli aggiornamenti.",
                    true,
                    0L);
        }
        // WAITING and any unexpected null retain the legacy one-second retry.
        return new DownloadPollingViewState(
                null, false, "Download in corso tramite Android…", false, 1_000L);
    }
}
