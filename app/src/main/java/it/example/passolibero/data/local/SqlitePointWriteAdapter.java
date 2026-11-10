package it.example.passolibero.data.local;

import android.content.ContentValues;
import android.database.sqlite.SQLiteOpenHelper;
import it.example.passolibero.application.session.PointWritePort;

/**
 * Preserves the eight legacy point columns, their insertion order and insertOrThrow semantics.
 * No new transaction, clock read, validation or additional write is introduced.
 */
public final class SqlitePointWriteAdapter implements PointWritePort {
    private final SQLiteOpenHelper helper;

    public SqlitePointWriteAdapter(SQLiteOpenHelper helper) {
        this.helper = helper;
    }

    @Override
    public void writePoint(
            long sessionId,
            long wallMs,
            double latitude,
            double longitude,
            double accuracyM,
            double speedKmh,
            double deltaM,
            String movement) {
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("t_wall", wallMs);
        values.put("lat", latitude);
        values.put("lon", longitude);
        values.put("accuracy_m", accuracyM);
        values.put("speed_kmh", speedKmh);
        values.put("delta_m", deltaM);
        values.put("movement", movement);
        helper.getWritableDatabase().insertOrThrow("points", null, values);
    }
}
