package it.example.passolibero.data.local;

import android.content.ContentValues;
import android.database.sqlite.SQLiteOpenHelper;
import it.example.passolibero.application.session.SessionCreationPort;

/**
 * Schema-v7 session insertion. Keep the legacy order: build ContentValues before opening the
 * writable database, then perform exactly one insertOrThrow without a new transaction.
 */
public final class SqliteSessionCreationAdapter implements SessionCreationPort {
    private final SQLiteOpenHelper helper;

    public SqliteSessionCreationAdapter(SQLiteOpenHelper helper) {
        this.helper = helper;
    }

    @Override
    public long createSession(long startedAtWallMs) {
        ContentValues values = new ContentValues();
        values.put("started_at", startedAtWallMs);
        return helper.getWritableDatabase().insertOrThrow("sessions", null, values);
    }
}
