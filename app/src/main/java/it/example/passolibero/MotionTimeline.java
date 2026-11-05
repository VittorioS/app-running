package it.example.passolibero;

/**
 * Monotonic-time interval ledger. Pending stop intervals are attributed to the
 * previous activity if motion resumes before confirmation. UI never shows a
 * stop candidate; a confirmed stop is backdated to the first strong evidence.
 */
public final class MotionTimeline {
    private static final long STRONG_STOP_MS = 4_000;
    private static final long MODERATE_STOP_MS = 8_000;
    private static final long START_CONFIRM_MS = 2_000;
    private static final long UNKNOWN_GRACE_MS = 5_000;
    private MovementClassifier.Kind committed = MovementClassifier.Kind.UNKNOWN;
    private MovementClassifier.Kind displayed = MovementClassifier.Kind.UNKNOWN;
    private long lastMs = -1, stopSince = -1, movingSince = -1, uncertainSince = -1;
    private long stopPendingMs, unresolvedMs;
    private long walkMs, runMs, stillMs, unknownMs, movingUnknownMs;

    public void advance(long nowMs, MotionEvidencePolicy.Evidence evidence,
                        MovementClassifier.Kind activity) {
        advance(nowMs, evidence, activity, 0);
    }

    public void advance(long nowMs, MotionEvidencePolicy.Evidence evidence,
                        MovementClassifier.Kind activity, long minimumStopMs) {
        if (lastMs < 0) { lastMs = nowMs; return; }
        if (nowMs <= lastMs) return;
        long dt = nowMs - lastMs;
        lastMs = nowMs;
        if (dt > UNKNOWN_GRACE_MS) {
            // A suspended worker is unobserved time, even when its first new
            // callback contains evidence of movement or stillness right now.
            finish();
            unknownMs += dt;
            committed = MovementClassifier.Kind.UNKNOWN;
            displayed = MovementClassifier.Kind.UNKNOWN;
            movingSince = -1;
            uncertainSince = -1;
            return;
        }
        if (evidence == MotionEvidencePolicy.Evidence.MOVING) {
            // Unobserved startup/downtime must not become RUN retrospectively.
            if (uncertainSince >= 0) {
                if (isMoving(committed)) credit(committed, unresolvedMs);
                else unknownMs += unresolvedMs;
                unresolvedMs = 0;
            }
            uncertainSince = -1;
            if (stopSince >= 0) {
                // Unconfirmed microstops belong to the preceding activity.
                credit(committed, stopPendingMs);
                stopPendingMs = 0;
                stopSince = -1;
            }
            if (!isMoving(committed)) {
                // Movement confirmation is independent of gait classification.
                if (movingSince < 0) movingSince = nowMs - dt;
                unresolvedMs += dt;
                if (nowMs - movingSince >= START_CONFIRM_MS) {
                    committed = (activity == MovementClassifier.Kind.RUN
                            || activity == MovementClassifier.Kind.WALK)
                            ? activity : MovementClassifier.Kind.MOVING_UNKNOWN;
                    displayed = committed;
                    credit(committed, unresolvedMs);
                    unresolvedMs = 0;
                    movingSince = -1;
                }
            } else {
                // Short uncertainty that recovers remains the preceding activity.
                credit(committed, unresolvedMs);
                unresolvedMs = 0;
                movingSince = -1;
                if (activity == MovementClassifier.Kind.RUN || activity == MovementClassifier.Kind.WALK) {
                    committed = activity;
                    displayed = activity;
                } else {
                    // Confirmed movement continues without a reliable gait estimate.
                    committed = MovementClassifier.Kind.MOVING_UNKNOWN;
                    displayed = committed;
                }
                credit(committed, dt);
            }
            return;
        }
        movingSince = -1;
        if (evidence == MotionEvidencePolicy.Evidence.STILL_STRONG
                || evidence == MotionEvidencePolicy.Evidence.STILL_MODERATE) {
            uncertainSince = -1;
            if (committed == MovementClassifier.Kind.STILL) {
                stillMs += unresolvedMs;
                unresolvedMs = 0;
                stillMs += dt;
                displayed = MovementClassifier.Kind.STILL;
                return;
            }
            if (stopSince < 0) stopSince = nowMs - dt;
            stopPendingMs += dt;
            long required = evidence == MotionEvidencePolicy.Evidence.STILL_STRONG
                    ? STRONG_STOP_MS : MODERATE_STOP_MS;
            required = Math.max(required, minimumStopMs);
            if (nowMs - stopSince >= required) {
                committed = MovementClassifier.Kind.STILL;
                displayed = MovementClassifier.Kind.STILL;
                stillMs += stopPendingMs;
                unknownMs += unresolvedMs;
                stopPendingMs = 0;
                unresolvedMs = 0;
                stopSince = -1;
            }
            return;
        }
        // Uncertain evidence never starts or confirms a stop.
        if (stopSince >= 0) {
            unknownMs += stopPendingMs;
            stopPendingMs = 0;
            stopSince = -1;
        }
        if (uncertainSince < 0) uncertainSince = nowMs - dt;
        if (nowMs - uncertainSince < UNKNOWN_GRACE_MS) {
            unresolvedMs += dt;
        } else {
            unknownMs += unresolvedMs + dt;
            unresolvedMs = 0;
            committed = MovementClassifier.Kind.UNKNOWN;
            displayed = MovementClassifier.Kind.UNKNOWN;
        }
    }

    /**
     * A cumulative report proves the time of its LAST step, not the times of
     * all earlier steps in the batch. Revoke only a pending overlapping stop;
     * leave that interval unknown rather than inventing continuous walking.
     */
    public void observeDelayedStep(long stepMs) {
        if (stopSince >= 0 && stepMs >= stopSince) {
            unknownMs += stopPendingMs;
            stopPendingMs = 0;
            stopSince = -1;
        }
    }

    private static boolean isMoving(MovementClassifier.Kind kind) {
        return kind == MovementClassifier.Kind.RUN || kind == MovementClassifier.Kind.WALK
                || kind == MovementClassifier.Kind.MOVING_UNKNOWN;
    }

    /** Resolve incomplete intervals without pretending unknown periods were stops. */
    public void finish() {
        // A session ending during an unconfirmed stop has no evidence of resumption.
        unknownMs += stopPendingMs;
        stopPendingMs = 0;
        stopSince = -1;
        unknownMs += unresolvedMs;
        unresolvedMs = 0;
    }
    private void credit(MovementClassifier.Kind kind, long dt) {
        if (dt <= 0) return;
        if (kind == MovementClassifier.Kind.RUN) runMs += dt;
        else if (kind == MovementClassifier.Kind.WALK) walkMs += dt;
        else if (kind == MovementClassifier.Kind.STILL) stillMs += dt;
        else if (kind == MovementClassifier.Kind.MOVING_UNKNOWN) movingUnknownMs += dt;
        else unknownMs += dt;
    }
    public MovementClassifier.Kind visible() { return displayed; }
    public long runMs() { return runMs; }
    public long walkMs() { return walkMs; }
    public long stillMs() { return stillMs; }
    public long unknownMs() { return unknownMs; }
    public long movingUnknownMs() { return movingUnknownMs; }
    public long movingMs() { return runMs + walkMs + movingUnknownMs; }
    public long unresolvedMs() { return unresolvedMs + stopPendingMs; }
    public void restore(long run, long walk, long still, long unknown) {
        restore(run, walk, still, unknown, 0);
    }
    public void restore(long run, long walk, long still, long unknown, long movingUnknown) {
        runMs = Math.max(0, run); walkMs = Math.max(0, walk);
        stillMs = Math.max(0, still); unknownMs = Math.max(0, unknown);
        movingUnknownMs = Math.max(0, movingUnknown);
        committed = MovementClassifier.Kind.UNKNOWN;
        displayed = MovementClassifier.Kind.UNKNOWN;
        lastMs = -1; stopSince = -1; movingSince = -1; uncertainSince = -1;
        stopPendingMs = 0; unresolvedMs = 0;
    }
}
