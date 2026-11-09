import it.example.passolibero.domain.GpsFixPolicy;

import java.util.Random;

/** Reference-equivalence probes for the original TrackingService GNSS decisions. */
public final class GpsFixPolicyTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void throttle(boolean busy, long previous, long current) {
        boolean expected = busy && previous >= 0
                && current >= previous && current - previous < 900L;
        check(GpsFixPolicy.skipNormalFix(busy, previous, current) == expected,
                "Normal GNSS throttle must equal the legacy sprint rule");
    }

    private static void plausibility(boolean uncertain, double speed, double nativeSpeed,
            double uncertainty, boolean moving, boolean still) {
        boolean expected = uncertain
                && Double.isFinite(speed) && speed >= 1.2 && speed < 20
                && Double.isFinite(nativeSpeed) && nativeSpeed >= 0.4
                && Double.isFinite(uncertainty) && uncertainty <= 0.6;
        boolean actual = GpsFixPolicy.plausibleSlow(
                uncertain, speed, nativeSpeed, uncertainty);
        check(actual == expected, "Uncertain GNSS slow-motion gate must be unchanged");
        boolean counted = moving || expected;
        if (still && !moving) counted = false;
        check(GpsFixPolicy.countDistance(moving, actual, still) == counted,
                "Stationary evidence must block unconfirmed GPS distance");
    }

    public static void main(String[] args) {
        for (long previous : new long[] {-1, 0, 1000, 20000})
            for (long fix : new long[] {0, 999, 1000, 1899, 1900, 20000, 20899, 20900})
                for (boolean sprint : new boolean[] {false, true})
                    throttle(sprint, previous, fix);
        for (double speed : new double[] {Double.NaN, -1, 0, 1.19, 1.2, 19.99, 20,
                40, Double.POSITIVE_INFINITY}) {
            for (double nativeSpeed : new double[] {Double.NaN, 0.39, 0.4, 3}) {
                for (double uncertainty : new double[] {Double.NaN, 0.6, 0.61, 0}) {
                    for (boolean uncertain : new boolean[] {false, true})
                        plausibility(uncertain, speed, nativeSpeed, uncertainty,
                                false, false);
                }
            }
        }
        Random random = new Random(60063);
        for (int i = 0; i < 1000; i++) {
            long old = random.nextInt(25000) - 1000;
            long incoming = random.nextInt(26000);
            throttle(random.nextBoolean(), old, incoming);
            plausibility(random.nextBoolean(), random.nextDouble() * 45,
                    random.nextDouble() * 4, random.nextDouble() * 2,
                    random.nextBoolean(), random.nextBoolean());
        }
        check(!GpsFixPolicy.skipNormalFix(true, 6000, 5500),
                "Out-of-order fixes must still reach the existing monotonic guard");
        check(!GpsFixPolicy.countDistance(false, true, true),
                "A still phase cannot acquire tentative distance");
        System.out.println("PASS: " + checks + " GNSS-policy equivalence assertions.");
    }
}
