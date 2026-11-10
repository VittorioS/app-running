package it.example.passolibero.domain.session;

/**
 * Immutable Android-free view of the fields persisted by the legacy RunState.
 *
 * <p>No range or null normalization: historical preferences (including non-finite values) must
 * round-trip exactly. The active marker and heartbeat belong to the preferences adapter.
 * The transient lastTickElapsedMs/lastStepElapsedMs are intentionally not persisted.
 */
public final class RunSnapshot {
    public final long id;
    public final long startedWallMs;
    public final long startedElapsedMs;
    public final double distanceM;
    public final double walkM;
    public final double runM;
    public final long walkMs;
    public final long runMs;
    public final long stillMs;
    public final long unknownMs;
    public final long movingUnknownMs;
    public final long movingMs;
    public final int steps;
    public final int runSteps;
    public final int walkSteps;
    public final int cadenceSpm;
    public final int goalM;
    public final boolean lastLocationValid;
    public final double lastLatitude;
    public final double lastLongitude;
    public final int checkpointIndex;
    public final int checkpointInterval;
    public final String movement;
    public final String gpsStatus;
    public final double speedKmh;
    public final double maxRunSpeedKmh;
    public final double gpsAccuracy;

    public RunSnapshot(
            long id,
            long startedWallMs,
            long startedElapsedMs,
            double distanceM,
            double walkM,
            double runM,
            long walkMs,
            long runMs,
            long stillMs,
            long unknownMs,
            long movingUnknownMs,
            long movingMs,
            int steps,
            int runSteps,
            int walkSteps,
            int cadenceSpm,
            int goalM,
            boolean lastLocationValid,
            double lastLatitude,
            double lastLongitude,
            int checkpointIndex,
            int checkpointInterval,
            String movement,
            String gpsStatus,
            double speedKmh,
            double maxRunSpeedKmh,
            double gpsAccuracy) {
        this.id = id;
        this.startedWallMs = startedWallMs;
        this.startedElapsedMs = startedElapsedMs;
        this.distanceM = distanceM;
        this.walkM = walkM;
        this.runM = runM;
        this.walkMs = walkMs;
        this.runMs = runMs;
        this.stillMs = stillMs;
        this.unknownMs = unknownMs;
        this.movingUnknownMs = movingUnknownMs;
        this.movingMs = movingMs;
        this.steps = steps;
        this.runSteps = runSteps;
        this.walkSteps = walkSteps;
        this.cadenceSpm = cadenceSpm;
        this.goalM = goalM;
        this.lastLocationValid = lastLocationValid;
        this.lastLatitude = lastLatitude;
        this.lastLongitude = lastLongitude;
        this.checkpointIndex = checkpointIndex;
        this.checkpointInterval = checkpointInterval;
        this.movement = movement;
        this.gpsStatus = gpsStatus;
        this.speedKmh = speedKmh;
        this.maxRunSpeedKmh = maxRunSpeedKmh;
        this.gpsAccuracy = gpsAccuracy;
    }
}
