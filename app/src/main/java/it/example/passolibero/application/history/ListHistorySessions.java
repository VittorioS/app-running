package it.example.passolibero.application.history;

import java.util.List;
import java.util.Objects;

/**
 * Read the bounded history list without coupling UI rendering to SQLite.
 *
 * <p>Ordering, nullable historic columns, and the 200-session limit are delegated
 * unchanged to the existing read port. The caller handles display and errors.
 */
public final class ListHistorySessions {
    private static final int HISTORY_SCREEN_LIMIT = 200;

    private final SessionReadPort sessions;

    public ListHistorySessions(SessionReadPort sessions) {
        this.sessions = Objects.requireNonNull(sessions);
    }

    public List<SessionReadPort.SessionRow> execute() {
        return sessions.recent(HISTORY_SCREEN_LIMIT);
    }
}
