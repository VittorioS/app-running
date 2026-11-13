package it.example.passolibero.presentation.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import it.example.passolibero.application.update.ReadDownloadState;
import org.junit.Test;

/** P8b/#34: characterizes exactly the legacy polling labels and UI side effects. */
public final class DownloadPollingViewStateTest {
    @Test
    public void successEnablesVerificationOnlyWithoutSchedulingAnotherPoll() {
        DownloadPollingViewState result =
                DownloadPollingViewState.from(ReadDownloadState.State.READY_TO_VERIFY);

        assertEquals("Verifica APK e installa", result.actionLabel);
        assertTrue(result.actionEnabled);
        assertFalse(result.clearSavedDownload);
        assertEquals(
                "Download terminato. Tocca per verificare firma e integrità.", result.statusText);
        assertEquals(0L, result.nextPollDelayMillis);
    }

    @Test
    public void failureClearsSavedDownloadAndKeepsTheExistingButtonLabel() {
        DownloadPollingViewState result =
                DownloadPollingViewState.from(ReadDownloadState.State.FAILED);

        assertNull(result.actionLabel);
        assertFalse(result.actionEnabled);
        assertTrue(result.clearSavedDownload);
        assertEquals(
                "Download non riuscito. Riprova il controllo degli aggiornamenti.",
                result.statusText);
        assertEquals(0L, result.nextPollDelayMillis);
    }

    @Test
    public void waitingDisablesButtonAndPollsAgainAfterExactlyOneSecond() {
        DownloadPollingViewState result =
                DownloadPollingViewState.from(ReadDownloadState.State.WAITING);

        assertNull(result.actionLabel);
        assertFalse(result.actionEnabled);
        assertFalse(result.clearSavedDownload);
        assertEquals("Download in corso tramite Android…", result.statusText);
        assertEquals(1_000L, result.nextPollDelayMillis);
    }

    @Test
    public void unexpectedNullStatusKeepsTheOriginalWaitingFallback() {
        DownloadPollingViewState result = DownloadPollingViewState.from(null);

        assertNull(result.actionLabel);
        assertFalse(result.actionEnabled);
        assertFalse(result.clearSavedDownload);
        assertEquals("Download in corso tramite Android…", result.statusText);
        assertEquals(1_000L, result.nextPollDelayMillis);
    }
}
