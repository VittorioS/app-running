package it.example.passolibero.application.session;

/** Write-only boundary for one GPS point; no Android or SQLite types. */
public interface PointWritePort {
    void writePoint(
            long sessionId,
            long wallMs,
            double latitude,
            double longitude,
            double accuracyM,
            double speedKmh,
            double deltaM,
            String movement);
}
