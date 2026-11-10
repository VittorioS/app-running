package it.example.passolibero.application.history;

import java.util.List;

/**
 * Read-only session/history port. SQLite and Android are implementation details in data/local.
 * Methods retain the legacy ordering, nullable columns and point pagination contract.
 */
public interface SessionReadPort {
    List<SessionRow> recent(int count);

    SessionRow session(long sessionId);

    List<PointRow> pointsAfter(long sessionId, long afterId, int limit);

    List<SprintRow> sprints(long sessionId);

    boolean hasTaggedSprintPoints(long sessionId, long fromWallMs, long untilWallMs);

    /** Read-only values; the mutable root-package UI model is adapted at the legacy boundary. */
    final class SessionRow {
        public final long id;
        public final long startedAt;
        public final long elapsedMs;
        public final double distance;
        public final int steps;
        public final double runM;
        public final double walkM;
        public final String status;
        public final long movingMs;
        public final long walkMs;
        public final long runMs;
        public final long stillMs;
        public final int goalM;
        public final long unknownMs;
        public final long movingUnknownMs;
        public final Integer runSteps;
        public final Integer walkSteps;
        public final Long endedAt;
        public final Double maxRunSpeedKmh;
        public final Integer checkpointIntervalM;

        public SessionRow(
                long id,
                long startedAt,
                long elapsedMs,
                double distance,
                int steps,
                double runM,
                double walkM,
                String status,
                long movingMs,
                long walkMs,
                long runMs,
                long stillMs,
                int goalM,
                long unknownMs,
                long movingUnknownMs,
                Integer runSteps,
                Integer walkSteps,
                Long endedAt,
                Double maxRunSpeedKmh,
                Integer checkpointIntervalM) {
            this.id = id;
            this.startedAt = startedAt;
            this.elapsedMs = elapsedMs;
            this.distance = distance;
            this.steps = steps;
            this.runM = runM;
            this.walkM = walkM;
            this.status = status;
            this.movingMs = movingMs;
            this.walkMs = walkMs;
            this.runMs = runMs;
            this.stillMs = stillMs;
            this.goalM = goalM;
            this.unknownMs = unknownMs;
            this.movingUnknownMs = movingUnknownMs;
            this.runSteps = runSteps;
            this.walkSteps = walkSteps;
            this.endedAt = endedAt;
            this.maxRunSpeedKmh = maxRunSpeedKmh;
            this.checkpointIntervalM = checkpointIntervalM;
        }
    }

    /** Read-only values; the mutable root-package UI model is adapted at the legacy boundary. */
    final class PointRow {
        public final long id;
        public final long time;
        public final double lat;
        public final double lon;
        public final String movement;
        public final double deltaM;
        public final double speedKmh;
        public final double accuracyM;

        public PointRow(
                long id,
                long time,
                double lat,
                double lon,
                String movement,
                double deltaM,
                double speedKmh,
                double accuracyM) {
            this.id = id;
            this.time = time;
            this.lat = lat;
            this.lon = lon;
            this.movement = movement;
            this.deltaM = deltaM;
            this.speedKmh = speedKmh;
            this.accuracyM = accuracyM;
        }
    }

    /** Read-only values; the mutable root-package UI model is adapted at the legacy boundary. */
    final class SprintRow {
        public final long id;
        public final long recordedAt;
        public final String mode;
        public final int target;
        public final int countdownSeconds;
        public final long elapsedMs;
        public final double distanceM;
        public final Double peakKmh;
        public final int samples;
        public final String outcome;

        public SprintRow(
                long id,
                long recordedAt,
                String mode,
                int target,
                int countdownSeconds,
                long elapsedMs,
                double distanceM,
                Double peakKmh,
                int samples,
                String outcome) {
            this.id = id;
            this.recordedAt = recordedAt;
            this.mode = mode;
            this.target = target;
            this.countdownSeconds = countdownSeconds;
            this.elapsedMs = elapsedMs;
            this.distanceM = distanceM;
            this.peakKmh = peakKmh;
            this.samples = samples;
            this.outcome = outcome;
        }
    }

}
