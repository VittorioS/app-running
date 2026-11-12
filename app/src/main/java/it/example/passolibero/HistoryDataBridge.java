package it.example.passolibero;

import android.content.Context;

import it.example.passolibero.application.history.ListHistorySessions;
import it.example.passolibero.application.history.SessionReadPort;
import it.example.passolibero.data.local.SqliteSessionReadAdapter;

import java.util.List;

/**
 * Legacy composition boundary for the History screen.
 *
 * <p>Keeps SQLite ownership and lifetime outside HistoryActivity while the rest
 * of that legacy screen (including deletion) is migrated in later small PRs.
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
}
