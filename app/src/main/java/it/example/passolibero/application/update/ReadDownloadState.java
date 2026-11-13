package it.example.passolibero.application.update;

import java.util.Objects;

/**
 * Android-free boundary for polling an already-enqueued APK download.
 *
 * <p>READY_TO_VERIFY only enables the existing hash, package, version and certificate checks.
 * It never represents permission to install an APK.
 */
public final class ReadDownloadState {
    public enum State {
        READY_TO_VERIFY,
        FAILED,
        WAITING
    }

    public interface Port {
        State read(long downloadId);
    }

    private final Port port;

    public ReadDownloadState(Port port) {
        this.port = Objects.requireNonNull(port);
    }

    public State execute(long downloadId) {
        return port.read(downloadId);
    }
}
