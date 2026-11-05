package it.example.passolibero;

/** Fresh WALK/RUN evidence, independent from movement/stop confirmation. */
public final class ActivityEvidence {
    private final MovementClassifier classifier = new MovementClassifier();
    private long lastFixMs = -1;
    private MovementClassifier.Kind activity = MovementClassifier.Kind.UNKNOWN;

    public void observe(long nowMs, long fixMs, double speedKmh, int recentSteps, double threshold) {
        if (!MovementClassifier.isFreshMeasurement(nowMs, fixMs) || fixMs <= lastFixMs
                || !Double.isFinite(speedKmh) || speedKmh < 0) return;
        activity = classifier.update(fixMs, speedKmh, true, recentSteps, 0, threshold);
        lastFixMs = fixMs;
    }

    public MovementClassifier.Kind current(long nowMs) {
        return MovementClassifier.isFreshMeasurement(nowMs, lastFixMs)
                && (activity == MovementClassifier.Kind.RUN || activity == MovementClassifier.Kind.WALK)
                ? activity : MovementClassifier.Kind.UNKNOWN;
    }

    public void onMotionState(MovementClassifier.Kind kind) {
        if (kind == MovementClassifier.Kind.STILL || kind == MovementClassifier.Kind.UNKNOWN) reset();
    }

    public void reset() {
        classifier.reset();
        lastFixMs = -1;
        activity = MovementClassifier.Kind.UNKNOWN;
    }
}
