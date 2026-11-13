package it.example.passolibero;

import android.content.Context;

import it.example.passolibero.application.history.ListHistorySessions;
import it.example.passolibero.application.history.SessionReadPort;
import it.example.passolibero.data.local.SqliteSessionReadAdapter;

import java.util.List;

/**
 * Legacy composition boundary for the History screen.
 *
 * <p>Owns SQLite helper lifetimes outside HistoryActivity while the screen
 * delegates reading and deletion to the application use cases.
 */
public final class HistoryDataBridge {
    private HistoryDataBridge() {}

    public static List<SessionReadPort.SessionRow> listRecent(Context context) {
        try (TrackerDb database = new TrackerDb(context)) {
            return new ListHistorySessions(
                    new SqliteSessionReadAdapter(database.getReadableDatabase()))
                    .execute();
        }
    }

    public static boolean deleteSession(Context context, long sessionId) {
        try (TrackerDb database = new TrackerDb(context)) {
            return database.deleteSession(sessionId);
        }
    }
}
