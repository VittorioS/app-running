package it.example.passolibero.application.export;

import java.io.IOException;

/**
 * Incremental export view of one session's GPS points.
 *
 * <p>The adapter must preserve ascending SQLite row ID, source values and a constant-memory cursor.
 * Do not impose the 2,000-point display paging cap on an exported route.
 */
public interface SessionGpsExportPort {
    void forEachPoint(long sessionId, Sink sink) throws IOException;

    @FunctionalInterface
    interface Sink {
        void accept(Point point) throws IOException;
    }

    /** Immutable raw values used by the existing JSON field order. */
    final class Point {
        public final long timestampUtcMs;
        public final double latitude;
        public final double longitude;
        public final double accuracyM;
        public final double speedKmh;
        public final double deltaM;
        public final String movement;

        public Point(
                long timestampUtcMs,
                double latitude,
                double longitude,
                double accuracyM,
                double speedKmh,
                double deltaM,
                String movement) {
            this.timestampUtcMs = timestampUtcMs;
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracyM = accuracyM;
            this.speedKmh = speedKmh;
            this.deltaM = deltaM;
            this.movement = movement;
        }
    }
}
