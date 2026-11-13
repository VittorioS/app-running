package it.example.passolibero.application.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/** P8b/#34: preserve download polling's three states without Android or network. */
public final class ReadDownloadStateTest {
    private static final class FakeStatusPort implements ReadDownloadState.Port {
        private ReadDownloadState.State answer;
        private long requestedId = -1;
        private int calls;

        FakeStatusPort(ReadDownloadState.State answer) {
            this.answer = answer;
        }

        @Override
        public ReadDownloadState.State read(long downloadId) {
            calls++;
            requestedId = downloadId;
            return answer;
        }
    }

    @Test
    public void successfulDownloadIsOnlyReadyForVerificationNotInstallation() {
        FakeStatusPort port =
                new FakeStatusPort(ReadDownloadState.State.READY_TO_VERIFY);
        ReadDownloadState read = new ReadDownloadState(port);
        assertSame(ReadDownloadState.State.READY_TO_VERIFY, read.execute(42L));
        assertEquals(42L, port.requestedId);
        assertEquals(1, port.calls);
    }

    @Test
    public void failedDownloadRemainsFailedWithoutRetryOrSideEffects() {
        FakeStatusPort port = new FakeStatusPort(ReadDownloadState.State.FAILED);
        assertSame(ReadDownloadState.State.FAILED, new ReadDownloadState(port).execute(7L));
        assertEquals(7L, port.requestedId);
        assertEquals(1, port.calls);
    }

    @Test
    public void pendingDownloadRemainsWaitingAndOnlyOneReadIsPerformed() {
        FakeStatusPort port = new FakeStatusPort(ReadDownloadState.State.WAITING);
        assertSame(ReadDownloadState.State.WAITING, new ReadDownloadState(port).execute(8L));
        assertEquals(8L, port.requestedId);
        assertEquals(1, port.calls);
    }
}
