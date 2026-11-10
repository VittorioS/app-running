package it.example.passolibero.application.session;

/** Read-only values and one command for a persisted session update. */
public interface SessionSnapshotWritePort {
    void updateSnapshot(Values values, boolean finished);

    /** Immutable payload; SQLite conversion belongs to data/local. */
    final class Values {
        public final double distanceM;
        public final long elapsedMs;
        public final long movingMs;
        public final int steps;
        public final int runSteps;
        public final int walkSteps;
        public final double maxRunSpeedKmh;
        public final long walkMs;
        public final long runMs;
        public final long stillMs;
        public final long unknownMs;
        public final long movingUnknownMs;
        public final double walkM;
        public final double runM;
        public final int goalM;
        public final int checkpointInterval;
        public final long sessionId;

        public Values(
                double distanceM,
                long elapsedMs,
                long movingMs,
                int steps,
                int runSteps,
                int walkSteps,
                double maxRunSpeedKmh,
                long walkMs,
                long runMs,
                long stillMs,
                long unknownMs,
                long movingUnknownMs,
                double walkM,
                double runM,
                int goalM,
                int checkpointInterval,
                long sessionId) {
            this.distanceM = distanceM;
            this.elapsedMs = elapsedMs;
            this.movingMs = movingMs;
            this.steps = steps;
            this.runSteps = runSteps;
            this.walkSteps = walkSteps;
            this.maxRunSpeedKmh = maxRunSpeedKmh;
            this.walkMs = walkMs;
            this.runMs = runMs;
            this.stillMs = stillMs;
            this.unknownMs = unknownMs;
            this.movingUnknownMs = movingUnknownMs;
            this.walkM = walkM;
            this.runM = runM;
            this.goalM = goalM;
            this.checkpointInterval = checkpointInterval;
            this.sessionId = sessionId;
        }
    }
}
