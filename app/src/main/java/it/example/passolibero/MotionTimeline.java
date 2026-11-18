package it.example.passolibero;

import it.example.passolibero.domain.tracking.ConfirmedMotionPolicy;
import it.example.passolibero.domain.tracking.ConfirmedMovingIntervalTransition;
import it.example.passolibero.domain.tracking.ConfirmedStillIntervalTransition;
import it.example.passolibero.domain.tracking.ConfirmedStillPolicy;
import it.example.passolibero.domain.tracking.DelayedStepStopRevocationTransition;
import it.example.passolibero.domain.tracking.MovingUncertaintyRecoveryTransition;
import it.example.passolibero.domain.tracking.MovingStartTransition;
import it.example.passolibero.domain.tracking.PendingStillTransition;
import it.example.passolibero.domain.tracking.StillEvidencePolicy;
import it.example.passolibero.domain.tracking.TimeCreditBucketPolicy;
import it.example.passolibero.domain.tracking.UncertainEvidenceTransition;
import it.example.passolibero.domain.tracking.UnobservedIntervalPolicy;

/**
 * Monotonic-time interval ledger. Pending stop intervals are attributed to the
 * previous activity if motion resumes before confirmation. UI never shows a
 * stop candidate; a confirmed stop is backdated to the first strong evidence.
 */
public final class MotionTimeline {
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
        if (UnobservedIntervalPolicy.isUnobservedGap(dt)) {
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
                MovingUncertaintyRecoveryTransition.Effect recovery =
                        MovingUncertaintyRecoveryTransition.settle(
                                committed == null ? null : committed.name(), unresolvedMs);
                if (recovery.destination()
                        == MovingUncertaintyRecoveryTransition.Destination
                                .PREVIOUS_CONFIRMED_MOVEMENT) {
                    credit(committed, recovery.creditMs());
                } else {
                    unknownMs += recovery.creditMs();
                }
                unresolvedMs = 0;
            }
            uncertainSince = -1;
            if (stopSince >= 0) {
                // Unconfirmed microstops belong to the preceding activity.
                credit(committed, stopPendingMs);
                stopPendingMs = 0;
                stopSince = -1;
            }
            if (!ConfirmedMotionPolicy.isMoving(committed == null ? null : committed.name())) {
                // Movement confirmation is independent of gait classification.
                MovingStartTransition.Effect start =
                        MovingStartTransition.evaluate(
                                nowMs,
                                dt,
                                movingSince,
                                unresolvedMs,
                                activity == null ? null : activity.name());
                movingSince = start.movingSinceMs();
                unresolvedMs = start.pendingMs();
                if (start.confirmed()) {
                    committed =
                            start.useCurrentGait()
                                    ? activity
                                    : MovementClassifier.Kind.MOVING_UNKNOWN;
                    displayed = committed;
                    credit(committed, unresolvedMs);
                    unresolvedMs = 0;
                    movingSince = -1;
                }
            } else {
                // Short uncertainty that recovers remains the preceding activity.
                ConfirmedMovingIntervalTransition.Effect effect =
                        ConfirmedMovingIntervalTransition.evaluate(
                                unresolvedMs, dt, activity == null ? null : activity.name());
                credit(committed, effect.previousCreditMs());
                unresolvedMs = 0;
                movingSince = -1;
                if (effect.useCurrentGait()) {
                    committed = activity;
                    displayed = activity;
                } else {
                    // Confirmed movement continues without a reliable gait estimate.
                    committed = MovementClassifier.Kind.MOVING_UNKNOWN;
                    displayed = committed;
                }
                credit(committed, effect.currentCreditMs());
            }
            return;
        }
        movingSince = -1;
        if (StillEvidencePolicy.isStill(evidence == null ? null : evidence.name())) {
            uncertainSince = -1;
            if (ConfirmedStillPolicy.isConfirmed(committed == null ? null : committed.name())) {
                ConfirmedStillIntervalTransition.Effect effect =
                        ConfirmedStillIntervalTransition.evaluate(unresolvedMs, dt);
                stillMs += effect.previousCreditMs();
                unresolvedMs = 0;
                stillMs += effect.currentCreditMs();
                displayed = MovementClassifier.Kind.STILL;
                return;
            }
            PendingStillTransition.Effect pending =
                    PendingStillTransition.evaluate(
                            nowMs,
                            dt,
                            stopSince,
                            stopPendingMs,
                            unresolvedMs,
                            evidence.name(),
                            minimumStopMs);
            stopSince = pending.stopSinceMs();
            stopPendingMs = pending.pendingStopMs();
            if (pending.confirmed()) {
                committed = MovementClassifier.Kind.STILL;
                displayed = MovementClassifier.Kind.STILL;
                stillMs += pending.stillCreditMs();
                unknownMs += pending.unknownCreditMs();
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
        UncertainEvidenceTransition.Effect effect =
                UncertainEvidenceTransition.resolve(nowMs - uncertainSince, unresolvedMs, dt);
        unresolvedMs = effect.unresolvedMsAfter();
        unknownMs += effect.unknownCreditMs();
        if (effect.invalidateConfirmedState()) {
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
        DelayedStepStopRevocationTransition.Effect effect =
                DelayedStepStopRevocationTransition.evaluate(stopSince, stepMs, stopPendingMs);
        if (effect.revokePendingStop()) {
            unknownMs += effect.unknownCreditMs();
            stopPendingMs = 0;
            stopSince = -1;
        }
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
        TimeCreditBucketPolicy.Bucket bucket =
                TimeCreditBucketPolicy.bucketFor(kind == null ? null : kind.name());
        if (bucket == TimeCreditBucketPolicy.Bucket.RUN) runMs += dt;
        else if (bucket == TimeCreditBucketPolicy.Bucket.WALK) walkMs += dt;
        else if (bucket == TimeCreditBucketPolicy.Bucket.STILL) stillMs += dt;
        else if (bucket == TimeCreditBucketPolicy.Bucket.MOVING_UNKNOWN) movingUnknownMs += dt;
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
