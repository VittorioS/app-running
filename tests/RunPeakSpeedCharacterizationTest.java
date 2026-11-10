import it.example.passolibero.RunPeakSpeedPolicy;

import java.util.Random;

/** Independent reference expression and edge-case fixtures for the legacy peak-speed policy. */
public final class RunPeakSpeedCharacterizationTest {
    private static int assertions;

    private static void same(double expected, double actual, String context) {
        assertions++;
        if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual)) {
            throw new AssertionError(context + ": expected " + expected + ", received " + actual);
        }
    }

    /** Independent pre-migration expression. Do not replace with a call to the new domain code. */
    private static double legacy(double currentMax, String movement, boolean moving,
            boolean fresh, double accuracyM, double acceptedMeters, double speedKmh) {
        if (!"RUN".equals(movement) || !moving || !fresh
                || !Double.isFinite(accuracyM) || accuracyM < 0 || accuracyM > 15
                || !Double.isFinite(acceptedMeters) || acceptedMeters <= 0
                || !Double.isFinite(speedKmh) || speedKmh <= 0 || speedKmh > 40)
            return currentMax;
        if (!Double.isFinite(currentMax) || currentMax <= 0) return speedKmh;
        return Math.max(currentMax, speedKmh);
    }

    private static void check(double currentMax, String movement, boolean moving, boolean fresh,
            double accuracyM, double acceptedMeters, double speedKmh) {
        double expected = legacy(currentMax, movement, moving, fresh, accuracyM, acceptedMeters, speedKmh);
        same(expected, RunPeakSpeedPolicy.update(currentMax, movement, moving, fresh,
                accuracyM, acceptedMeters, speedKmh), "legacy facade parity");
        same(expected, it.example.passolibero.domain.tracking.RunPeakSpeedPolicy.update(
                currentMax, movement, moving, fresh, accuracyM, acceptedMeters, speedKmh),
                "domain parity");
    }

    public static void main(String[] args) {
        double[] current = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                -0.0, 0, 9.5, 40, 1e100};
        double[] accuracy = {Double.NaN, Double.NEGATIVE_INFINITY, -1, 0, 14.9999,
                15, Math.nextUp(15.0), 20, Double.POSITIVE_INFINITY};
        double[] delta = {Double.NaN, Double.NEGATIVE_INFINITY, -1, -0.0, 0,
                Double.MIN_VALUE, 0.1, 100, Double.POSITIVE_INFINITY};
        double[] speed = {Double.NaN, Double.NEGATIVE_INFINITY, -1, 0, 0.00001,
                7.5, 40, Math.nextUp(40.0), Double.POSITIVE_INFINITY};
        String[] gait = {"RUN", "WALK", "STILL", "MOVING_UNKNOWN", "UNKNOWN",
                "SPRINT", "run", "", null};

        for (double a : accuracy)
            for (double d : delta)
                for (double v : speed)
                    check(Double.NaN, "RUN", true, true, a, d, v);
        for (double m : current)
            for (String k : gait)
                for (boolean moving : new boolean[] {false, true})
                    for (boolean fresh : new boolean[] {false, true})
                        check(m, k, moving, fresh, 3.0, 1.0, 12.0);

        Random rng = new Random(19019L);
        for (int n = 0; n < 5000; n++) {
            double m = current[rng.nextInt(current.length)];
            String k = gait[rng.nextInt(gait.length)];
            boolean moving = rng.nextBoolean();
            boolean fresh = rng.nextBoolean();
            double a = rng.nextBoolean() ? accuracy[rng.nextInt(accuracy.length)]
                    : (rng.nextDouble() * 25);
            double d = rng.nextBoolean() ? delta[rng.nextInt(delta.length)]
                    : rng.nextDouble() * 10;
            double v = rng.nextBoolean() ? speed[rng.nextInt(speed.length)]
                    : rng.nextDouble() * 50;
            check(m, k, moving, fresh, a, d, v);
        }

        double maximum = Double.NaN;
        double expected = Double.NaN;
        for (int n = 0; n < 50; n++) {
            double v = n % 3 == 0 ? 22 : n % 3 == 1 ? 8 : 18;
            maximum = RunPeakSpeedPolicy.update(maximum, "RUN", true, true, 4, 2, v);
            expected = legacy(expected, "RUN", true, true, 4, 2, v);
            same(expected, maximum, "sequential record");
        }
        System.out.println("PASS: " + assertions + " independent peak-speed equivalence checks.");
    }
}
