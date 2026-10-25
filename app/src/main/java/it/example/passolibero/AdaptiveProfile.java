package it.example.passolibero;

/** Micro-modello online: due centroidi velocità, senza framework ML.
 * Auto-training limitato a esempi molto chiari; esempi etichettati dall'utente
 * sono sempre preferiti. Mai imparare soltanto dalle proprie previsioni al confine.
 */
public final class AdaptiveProfile {
    public double walkMean = 0, runMean = 0;
    public int walkSamples = 0, runSamples = 0;

    public void observe(boolean running, double speedKmh) {
        if (!Double.isFinite(speedKmh) || speedKmh <= 0.5 || speedKmh > 25) return;
        if (running) {
            runMean = runSamples == 0 ? speedKmh : runMean * 0.96 + speedKmh * 0.04;
            runSamples = Math.min(100_000, runSamples + 1);
        } else {
            walkMean = walkSamples == 0 ? speedKmh : walkMean * 0.96 + speedKmh * 0.04;
            walkSamples = Math.min(100_000, walkSamples + 1);
        }
    }

    public double threshold(double manual, boolean enabled) {
        if (!enabled || walkSamples < 12 || runSamples < 12 || runMean - walkMean < 0.8) return manual;
        double middle = (runMean + walkMean) / 2;
        return RunMath.clamp(middle, Math.max(3, manual - 1.5), Math.min(12, manual + 1.5));
    }

    public void clear() { walkMean = 0; runMean = 0; walkSamples = 0; runSamples = 0; }
}
