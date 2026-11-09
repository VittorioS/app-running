import it.example.passolibero.ActivityEvidence;
import it.example.passolibero.DistanceAccumulator;
import it.example.passolibero.MotionEvidencePolicy;
import it.example.passolibero.MotionTimeline;
import it.example.passolibero.MovementClassifier;
import it.example.passolibero.StepCounterEvidence;

/**
 * Fixed, timestamped synthetic GNSS/step traces. No Android sensor, clock,
 * network, device permission or random input is involved.
 */
public final class TrackingReplayTest {
    private static int checks;

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    private static void conserved(MotionTimeline ledger, long elapsed) {
        ledger.finish();
        check(ledger.movingMs() == ledger.runMs()
                + ledger.walkMs() + ledger.movingUnknownMs(), "moving breakdown");
        check(ledger.movingMs() + ledger.stillMs() + ledger.unknownMs() == elapsed,
                "No doubled or missing elapsed intervals: " + elapsed);
    }

    /** No step detector or step counter is available: GNSS can prove motion,
     * but missing step events cannot prove stillness. */
    private static void noStepSensorWithGpsDropout() {
        MotionEvidencePolicy evidence = new MotionEvidencePolicy();
        MotionTimeline ledger = new MotionTimeline();
        // Columns: measurement time, latitude, longitude, GNSS native speed.
        double[][] fixes = {
                {0, 40.85000, 14.27000, 2.0},
                {1000, 40.85002, 14.27000, 2.0},
                {2000, 40.85004, 14.27000, 2.0},
                {3000, 40.85006, 14.27000, 2.0}
        };
        for (double[] fix : fixes) {
            long t = (long) fix[0];
            evidence.observeGps(t, fix[1], fix[2], 4, fix[3], 0.1);
            MotionEvidencePolicy.Evidence status = evidence.evaluate(
                    t, 0, Long.MAX_VALUE, false, false, false, false);
            ledger.advance(t, status, MovementClassifier.Kind.UNKNOWN);
        }
        check(ledger.movingUnknownMs() >= 2000,
                "Two fresh GNSS fixes prove movement without requiring a step counter");
        check(ledger.walkMs() == 0 && ledger.runMs() == 0,
                "GNSS movement alone must not invent a RUN/WALK category");

        // Simulate a sensor/worker blackout: do not backfill unobserved time.
        ledger.advance(15000, evidence.evaluate(
                15000, 0, Long.MAX_VALUE, false, false, false, false),
                MovementClassifier.Kind.UNKNOWN);
        check(ledger.unknownMs() >= 12000,
                "A long location blackout cannot be guessed as movement or stillness");

        // A late/out-of-order fix cannot restore freshness.
        evidence.observeGps(2000, 40.86, 14.28, 4, 10, 0.1);
        check(evidence.evaluate(16000, 0, Long.MAX_VALUE,
                false, false, false, false) == MotionEvidencePolicy.Evidence.UNCERTAIN,
                "Stale and out-of-order fix cannot prove motion");

        for (long t = 16000; t <= 19000; t += 1000) {
            evidence.observeGps(t, 40.85 + t * 0.00000002, 14.27, 4, 2, 0.1);
            ledger.advance(t, evidence.evaluate(
                    t, 0, Long.MAX_VALUE, false, false, false, false),
                    MovementClassifier.Kind.UNKNOWN);
        }
        check(ledger.movingUnknownMs() > 2000, "Fresh GNSS after a gap can reconfirm motion");
        conserved(ledger, 19000);
    }

    private static void outOfOrderGaitAndRestart() {
        ActivityEvidence activity = new ActivityEvidence();
        for (long t = 0; t <= 4000; t += 1000)
            activity.observe(t, t, 10, 12, 6);
        check(activity.current(4000) == MovementClassifier.Kind.RUN,
                "RUN confirmed only by fresh measurements");
        activity.observe(6000, 2000, 2, 0, 6);
        check(activity.current(6000) == MovementClassifier.Kind.RUN,
                "Late fix cannot overwrite the last reliable RUN measurement");
        check(activity.current(8000) == MovementClassifier.Kind.UNKNOWN,
                "RUN expires after GPS dropout");
        activity.onMotionState(MovementClassifier.Kind.STILL);
        activity.observe(15000, 15000, 4, 2, 6);
        check(activity.current(15000) == MovementClassifier.Kind.UNKNOWN,
                "Stop/restart cannot inherit RUN from a previous interval");
        activity.observe(16000, 16000, 4, 2, 6);
        activity.observe(17000, 17000, 4, 2, 6);
        check(activity.current(17000) == MovementClassifier.Kind.WALK,
                "New walking requires fresh confirmation after a stop");
    }

    private static void delayedCounterDoesNotInventHistoricalGait() {
        StepCounterEvidence counter = new StepCounterEvidence();
        check(counter.observe(0, 100, 0) == 0, "Initial absolute counter is only a baseline");
        MotionTimeline ledger = new MotionTimeline();
        ledger.advance(0, MotionEvidencePolicy.Evidence.UNCERTAIN,
                MovementClassifier.Kind.UNKNOWN);
        for (long t = 1000; t <= 9000; t += 1000) {
            ledger.advance(t, MotionEvidencePolicy.Evidence.STILL_MODERATE,
                    MovementClassifier.Kind.UNKNOWN, StepCounterEvidence.STOP_CONFIRM_MS);
        }
        check(ledger.stillMs() == 0, "Wait for counter delivery before confirming a stop");
        check(counter.observe(5000, 110, 14000) == 10, "Delayed step batch remains counted");
        check(!counter.progressRecent(14000), "Delayed steps are not live movement");
        check(counter.canAmendPendingStop(14000), "Pending stop can be revisited");
        ledger.observeDelayedStep(counter.lastStepMs());
        check(ledger.unknownMs() == 9000,
                "Overlapping delayed batch revokes stop without inventing historical walking");
        check(ledger.runMs() == 0 && ledger.walkMs() == 0, "No synthetic gait");
        check(counter.observe(4000, 999, 14000) == 0, "Out-of-order batch rejected");
        check(counter.observe(16000, 120, 15000) == 0, "Future counter batch rejected");
        conserved(ledger, 9000);
    }

    private static void jitterAndFixGapDoNotFabricateDistance() {
        DistanceAccumulator gps = new DistanceAccumulator();
        check(gps.accept(40.85000, 14.27, 4, false) == 0, "Anchor when stopped");
        for (int i = 1; i <= 3; i++) {
            check(gps.accept(40.85000 + i * 0.000001, 14.27, 4, false) == 0,
                    "Stationary GNSS jitter does not count as distance");
        }
        // Small consecutive real movements accumulate against the stable anchor.
        double meters = 0;
        for (int i = 1; i <= 9; i++)
            meters += gps.accept(40.850003 + i * 0.00001, 14.27, 4, true);
        check(meters > 8 && meters < 15, "Cumulative GNSS microsteps retain real distance");
        gps.reset(); // Real service resets the path after a tracking gap.
        check(gps.accept(41.1, 14.9, 5, true) == 0,
                "Reacquiring a distant GPS point must not bridge a dropped track");
    }

    public static void main(String[] args) {
        noStepSensorWithGpsDropout();
        outOfOrderGaitAndRestart();
        delayedCounterDoesNotInventHistoricalGait();
        jitterAndFixGapDoNotFabricateDistance();
        System.out.println("PASS: " + checks + " deterministic GNSS/step replay assertions.");
    }
}
