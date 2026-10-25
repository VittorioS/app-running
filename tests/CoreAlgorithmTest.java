import it.example.passolibero.AdaptiveProfile;
import it.example.passolibero.DistanceAccumulator;
import it.example.passolibero.MovementClassifier;
import it.example.passolibero.RunMath;
import it.example.passolibero.RunStats;
import it.example.passolibero.RouteSegmenter;

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
        // Simula 100 m di cammino con letture GPS ogni ~1 metro (1 Hz).
        // Il precedente filtro scartava quasi tutti questi incrementi.
        DistanceAccumulator distance = new DistanceAccumulator();
        double total = 0;
        for (int meters = 0; meters <= 100; meters++) {
            total += distance.accept(0, meters / 111_195.0, 5, true);
        }
        check(total >= 95 && total <= 101,
                "100 m a piccoli passi devono essere contati (ottenuti: " + total + ")");
        // Fermi, le oscillazioni attorno al punto non devono gonfiare i metri.
        distance.reset();
        double stopped = 0;
        for (int i = 0; i < 100; i++) {
            stopped += distance.accept(0, (i % 2) * 2 / 111_195.0, 5, false);
        }
        check(stopped == 0, "Nessun incremento GPS da fermo");
        // Dopo un buco GPS non collegare posizioni distanti.
        distance.reset();
        check(distance.accept(0, 1, 5, true) == 0, "Nuova ancora dopo buco GPS");
        check(RunStats.instantPace(12.0).equals("5:00"), "Ritmo attuale espresso in min/km");
        check(RunStats.instantPace(0).equals("--:--"), "Ritmo assente da fermo");
        check(RunStats.nextCheckpointM(875, 250) == 1000, "Prossimo checkpoint");
        check(RunStats.remainingCheckpointM(875, 250) == 125, "Metri mancanti");
        check(RunStats.nextCheckpointM(1000, 500) == 1500, "Checkpoint successivo esatto");
        check(RunStats.goalProgress(2500, 5000) == 50, "50 percento dell'obiettivo");
        check(RunStats.goalRemaining(5100, 5000) == 0, "Obiettivo raggiunto");
        check(RouteSegmenter.isGap(1_000, 0, 0, 40_000, 0, 0), "Buchi GPS non collegati");
        check(!RouteSegmenter.isGap(1_000, 0, 0, 2_000, 0, 0.00001), "Percorso continuo");
        System.out.println("PASS: 21 controlli incluse distanza, ritmo, checkpoint, obiettivi e gap GPS.");
    }
}
