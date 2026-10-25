import it.example.passolibero.AdaptiveProfile;
import it.example.passolibero.MovementClassifier;
import it.example.passolibero.RunMath;

public final class CoreAlgorithmTest {
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check(Math.abs(RunMath.distanceMeters(0,0,0,0.001) - 111.19) < 0.5, "Haversine");
        check(RunMath.pace(300_000, 1000).equals("5:00"), "Pace 5:00 /km");
        check(RunMath.duration(3_670_000).equals("1:01:10"), "Duration");
        AdaptiveProfile profile = new AdaptiveProfile();
        check(profile.threshold(6, true) == 6, "Cold start");
        for (int i=0; i<40; i++) { profile.observe(false, 5.0); profile.observe(true, 7.5); }
        check(Math.abs(profile.threshold(6, true)-6.25) < 0.05, "Personal threshold");
        check(profile.threshold(6, false) == 6, "Manual override");

        MovementClassifier model = new MovementClassifier();
        for (int t = 0; t < 30; t++) model.update(t*1000L, 8.0, true, 12, 0, 6);
        check(model.current() == MovementClassifier.Kind.RUN, "RUN after confirmation");
        for (int t = 30; t < 60; t++) model.update(t*1000L, 5.0, true, 12, 0, 6);
        check(model.current() == MovementClassifier.Kind.WALK, "RUN->WALK without session stop");
        for (int t = 60; t < 65; t++) model.update(t*1000L, Double.NaN, false, 0, 100_000, 6);
        check(model.current() == MovementClassifier.Kind.WALK, "GPS gap does not invent motion");
        System.out.println("PASS: 9 controlli su metriche, soglie e classificazione.");
    }
}
