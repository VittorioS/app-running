package it.example.passolibero.data.local;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import it.example.passolibero.application.export.SessionGpsExportPort;
import java.io.IOException;
import java.util.Objects;

/**
 * Streams every stored GPS row for a single session in legacy ascending ID order.
 *
 * <p>The query deliberately has no page limit, and the cursor closes on success or failure.
 */
public final class SqliteSessionGpsExportAdapter implements SessionGpsExportPort {
    private final SQLiteDatabase database;

    public SqliteSessionGpsExportAdapter(SQLiteDatabase database) {
        this.database = Objects.requireNonNull(database);
    }

    @Override
    public void forEachPoint(long sessionId, Sink sink) throws IOException {
        try (Cursor cursor =
                database.query(
                        "points",
                        new String[] {
                            "t_wall",
                            "lat",
                            "lon",
                            "accuracy_m",
                            "speed_kmh",
                            "delta_m",
                            "movement"
                        },
                        "session_id=?",
                        new String[] {Long.toString(sessionId)},
                        null,
                        null,
                        "id ASC")) {
            while (cursor.moveToNext()) {
                sink.accept(
                        new Point(
                                cursor.getLong(0),
                                cursor.getDouble(1),
                                cursor.getDouble(2),
                                cursor.getDouble(3),
                                cursor.getDouble(4),
                                cursor.getDouble(5),
                                cursor.getString(6)));
            }
        }
    }
}
