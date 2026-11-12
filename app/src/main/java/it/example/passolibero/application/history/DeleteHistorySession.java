package it.example.passolibero.application.history;

import java.util.Objects;

/**
 * Delete one saved history session without coupling the UI to SQLite or Android.
 *
 * <p>Invalid IDs are rejected before storage access. An active session and a missing row remain
 * distinguishable from success through the existing boolean result.
 */
public final class DeleteHistorySession {
    private final SessionDeletePort deletePort;

    public DeleteHistorySession(SessionDeletePort deletePort) {
        this.deletePort = Objects.requireNonNull(deletePort);
    }

    public boolean execute(long sessionId) {
        return sessionId > 0 && deletePort.deleteSession(sessionId);
    }
}
