package it.example.passolibero.application.session;

/** Write-only contract for one completed or cancelled sprint row. */
public interface SessionSprintWritePort {
    void insertSprint(Values values);

    /** Immutable Android-free payload; validation of SprintTrial phase stays at the legacy edge. */
    final class Values {
        public final long sessionId;
        public final long recordedAtWallMs;
        public final String mode;
        public final int target;
        public final int countdownSeconds;
        public final long elapsedMs;
        public final double distanceM;
        public final double peakSpeedKmh;
        public final double averageSpeedKmh;
        public final int samples;
        public final String outcome;

        public Values(
                long sessionId,
                long recordedAtWallMs,
                String mode,
                int target,
                int countdownSeconds,
                long elapsedMs,
                double distanceM,
                double peakSpeedKmh,
                double averageSpeedKmh,
                int samples,
                String outcome) {
            this.sessionId = sessionId;
            this.recordedAtWallMs = recordedAtWallMs;
            this.mode = mode;
            this.target = target;
            this.countdownSeconds = countdownSeconds;
            this.elapsedMs = elapsedMs;
            this.distanceM = distanceM;
            this.peakSpeedKmh = peakSpeedKmh;
            this.averageSpeedKmh = averageSpeedKmh;
            this.samples = samples;
            this.outcome = outcome;
        }
    }
}
