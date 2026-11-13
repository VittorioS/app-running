package it.example.passolibero.application.export;

import java.io.IOException;
import java.util.Objects;

/** Delegate full, incremental GPS export to a read port without buffering or reordering points. */
public final class StreamSessionGpsPoints {
    private final SessionGpsExportPort points;

    public StreamSessionGpsPoints(SessionGpsExportPort points) {
        this.points = Objects.requireNonNull(points);
    }

    public void execute(long sessionId, SessionGpsExportPort.Sink sink) throws IOException {
        points.forEachPoint(sessionId, sink);
    }
}
