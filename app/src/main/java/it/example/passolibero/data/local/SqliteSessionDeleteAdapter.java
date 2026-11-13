package it.example.passolibero.data.local;

import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import it.example.passolibero.application.history.SessionDeletePort;

/**
 * SQLite v7 history deletion adapter, retaining the original guarded transaction.
 *
 * <p>The active-session preference is checked before and inside the transaction. A failed
 * child-table delete rolls back removal of the parent session.
 */
public final class SqliteSessionDeleteAdapter implements SessionDeletePort {
    private final SQLiteOpenHelper helper;
    private final SharedPreferences preferences;

    public SqliteSessionDeleteAdapter(SQLiteOpenHelper helper, SharedPreferences preferences) {
        this.helper = helper;
        this.preferences = preferences;
    }

    @Override
    public boolean deleteSession(long sessionId) {
        if (sessionId <= 0) return false;
        if (isCurrentSession(sessionId)) return false;

        SQLiteDatabase database = helper.getWritableDatabase();
        String[] args = {Long.toString(sessionId)};
        database.beginTransaction();
        try {
            if (isCurrentSession(sessionId)) return false;
            int removed = database.delete("sessions", "id=?", args);
            if (removed != 1) return false;
            database.delete("points", "session_id=?", args);
            database.delete("sprints", "session_id=?", args);
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    private boolean isCurrentSession(long sessionId) {
        return preferences.getBoolean("active", false)
                && preferences.getLong("active_id", 0) == sessionId;
    }
}
