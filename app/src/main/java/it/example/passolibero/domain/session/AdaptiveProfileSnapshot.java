package it.example.passolibero.domain.session;

/** Immutable persisted fields of the adaptive speed profile; no Android dependency. */
public final class AdaptiveProfileSnapshot {
    public final int walkSamples;
    public final int runSamples;
    public final double walkMean;
    public final double runMean;

    public AdaptiveProfileSnapshot(
            int walkSamples, int runSamples, double walkMean, double runMean) {
        this.walkSamples = walkSamples;
        this.runSamples = runSamples;
        this.walkMean = walkMean;
        this.runMean = runMean;
    }
}
