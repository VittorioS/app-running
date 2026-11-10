package it.example.passolibero;

import android.content.SharedPreferences;

import it.example.passolibero.data.local.PreferencesSnapshotMapper;
import it.example.passolibero.domain.session.RunSnapshot;

/** Stato minimo persistito una volta al secondo e dopo ogni update significativo. */
public final class RunState {
    public long id, startedWallMs, startedElapsedMs, lastTickElapsedMs;
    public long movingMs, walkMs, runMs, stillMs, unknownMs, movingUnknownMs;
    public int steps, runSteps, walkSteps, cadenceSpm, checkpointIndex, checkpointInterval, goalM;
    public double distanceM, walkM, runM, speedKmh, gpsAccuracy;
    // NaN significa nessun campione di corsa affidabile (non zero km/h).
    public double maxRunSpeedKmh = Double.NaN;
    public double lastLatitude, lastLongitude;
    public boolean lastLocationValid;
    public String movement = "UNKNOWN", gpsStatus = "In attesa del GPS";
    public long lastStepElapsedMs;

    /** Legacy mutable facade; preferences mapping now lives in data/local. */
    public static RunState read(SharedPreferences p) {
        RunSnapshot snapshot = PreferencesSnapshotMapper.readRun(p);
        RunState s = new RunState();
        s.id = snapshot.id;
        s.startedWallMs = snapshot.startedWallMs;
        s.startedElapsedMs = snapshot.startedElapsedMs;
        s.distanceM = snapshot.distanceM;
        s.walkM = snapshot.walkM;
        s.runM = snapshot.runM;
        s.walkMs = snapshot.walkMs;
        s.runMs = snapshot.runMs;
        s.stillMs = snapshot.stillMs;
        s.unknownMs = snapshot.unknownMs;
        s.movingUnknownMs = snapshot.movingUnknownMs;
        s.movingMs = snapshot.movingMs;
        s.steps = snapshot.steps;
        s.runSteps = snapshot.runSteps;
        s.walkSteps = snapshot.walkSteps;
        s.cadenceSpm = snapshot.cadenceSpm;
        s.goalM = snapshot.goalM;
        s.lastLocationValid = snapshot.lastLocationValid;
        s.lastLatitude = snapshot.lastLatitude;
        s.lastLongitude = snapshot.lastLongitude;
        s.checkpointIndex = snapshot.checkpointIndex;
        s.checkpointInterval = snapshot.checkpointInterval;
        s.movement = snapshot.movement;
        s.gpsStatus = snapshot.gpsStatus;
        s.speedKmh = snapshot.speedKmh;
        s.maxRunSpeedKmh = snapshot.maxRunSpeedKmh;
        s.gpsAccuracy = snapshot.gpsAccuracy;
        return s;
    }

    public void save(SharedPreferences p, boolean active) {
        PreferencesSnapshotMapper.saveRun(
                p,
                new RunSnapshot(
                        id,
                        startedWallMs,
                        startedElapsedMs,
                        distanceM,
                        walkM,
                        runM,
                        walkMs,
                        runMs,
                        stillMs,
                        unknownMs,
                        movingUnknownMs,
                        movingMs,
                        steps,
                        runSteps,
                        walkSteps,
                        cadenceSpm,
                        goalM,
                        lastLocationValid,
                        lastLatitude,
                        lastLongitude,
                        checkpointIndex,
                        checkpointInterval,
                        movement,
                        gpsStatus,
                        speedKmh,
                        maxRunSpeedKmh,
                        gpsAccuracy),
                active);
    }
}
