import it.example.passolibero.*;
import java.util.Random;

/** Replay timestamped sensor scenarios, including delayed delivery. */
public final class MotionRegressionTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void conserved(MotionTimeline t, long expected) {
        t.finish();
        check(t.movingMs() + t.stillMs() + t.unknownMs() == expected,
                "Every interval counted exactly once: " + expected);
        check(t.movingMs() == t.runMs() + t.walkMs() + t.movingUnknownMs(),
                "Movement includes unknown gait");
    }
    public static void main(String[] args) {
        MotionTimeline t = new MotionTimeline();
        t.advance(0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        for (int i=1; i<=12; i++) t.advance(i*1000L,
                MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        check(t.visible() == MovementClassifier.Kind.MOVING_UNKNOWN, "Movement without GPS gait");
        check(t.movingMs() == 12000 && t.movingUnknownMs() == 12000 && t.unknownMs() == 0,
                "Confirmed movement is not unknown time");
        // Brief sensor uncertainty and a microstop preserve movement, including unknown gait.
        t.advance(13000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        t.advance(14000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        t.advance(15000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        t.advance(16000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        check(t.movingUnknownMs() == 16000, "Short uncertainty does not erase confirmed movement");
        t.advance(17000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.WALK);
        check(t.walkMs() == 1000, "Unknown gait can become walking without another start delay");
        conserved(t, 17000);
        MotionTimeline restored = new MotionTimeline();
        restored.restore(t.runMs(), t.walkMs(), t.stillMs(), t.unknownMs(), t.movingUnknownMs());
        restored.advance(20000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        restored.advance(21000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        restored.advance(22000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        check(restored.movingUnknownMs() == 18000, "Unknown gait survives recovery");
        conserved(restored, 19000);

        ActivityEvidence activity = new ActivityEvidence();
        for (int i=0; i<=4; i++) activity.observe(i*1000L, i*1000L, 10, 12, 6);
        check(activity.current(4000) == MovementClassifier.Kind.RUN, "Running established");
        activity.observe(6000, 2000, 4, 2, 6); // Stale and out of order.
        check(activity.current(6000) == MovementClassifier.Kind.RUN, "Late fix cannot rewrite gait");
        check(activity.current(7000) == MovementClassifier.Kind.UNKNOWN, "Gait expires by measurement time");
        activity.observe(14000, 6000, 10, 12, 6);
        check(activity.current(14000) == MovementClassifier.Kind.UNKNOWN, "Delivery does not refresh old GPS");
        activity.onMotionState(MovementClassifier.Kind.STILL);
        activity.observe(15000, 15000, 4, 2, 6);
        check(activity.current(15000) == MovementClassifier.Kind.UNKNOWN, "Restart does not inherit RUN");
        activity.observe(16000, 16000, 4, 2, 6);
        activity.observe(17000, 17000, 4, 2, 6);
        check(activity.current(17000) == MovementClassifier.Kind.WALK, "Fresh walking confirmed after stop");
        activity.onMotionState(MovementClassifier.Kind.UNKNOWN);
        check(activity.current(17000) == MovementClassifier.Kind.UNKNOWN, "Evidence loss resets gait");
        check(!MovementClassifier.isFreshMeasurement(1000, 2000), "Future fixes rejected");
        MovementClassifier gap = new MovementClassifier();
        for (int i=0; i<=4; i++) gap.update(i*1000L, 10, true, 12, 0, 6);
        gap.update(5000, 4, true, 2, 0, 6);
        check(gap.update(35000, 4, true, 2, 0, 6) == MovementClassifier.Kind.UNKNOWN,
                "Two samples separated by a blackout do not confirm a category");

        InertialGaitDetector shakes = new InertialGaitDetector();
        for (int ms=0; ms<=2400; ms+=20) {
            boolean pulse = ms==200 || ms==500 || ms==1600 || ms==1900;
            shakes.observe(ms, 0, 0, pulse ? 12.81 : 9.81);
        }
        check(!shakes.gaitAt(2400), "Irregular hand movements are not gait");
        for (int sampleMs : new int[]{20, 40, 100}) {
            InertialGaitDetector regular = new InertialGaitDetector();
            for (int ms=0; ms<6000; ms+=sampleMs)
                regular.observe(ms, 0, 0, 9.81 + 2.5*Math.sin(ms*2*Math.PI/650));
            check(regular.gaitAt(6000), "Regular gait at sampling interval " + sampleMs);
            check(!regular.gaitAt(8000), "Stale acceleration expires");
        }
        MotionEvidencePolicy evidence = new MotionEvidencePolicy();
        for (int i=0; i<10; i++) {
            evidence.observeGps(i*1000L, 0, 0, 5, 0.1, 0.1);
            check(evidence.evaluate(i*1000L, 0, Long.MAX_VALUE, false, false, false, true)
                    == MotionEvidencePolicy.Evidence.UNCERTAIN, "GPS still + raw acceleration conflict");
        }
        check(evidence.evaluate(9000, 3, 0, true, true, false, true)
                == MotionEvidencePolicy.Evidence.MOVING, "Real steps override stationary GPS");
        evidence.reset();
        check(evidence.evaluate(0, 0, Long.MAX_VALUE, false, false, false, true)
                == MotionEvidencePolicy.Evidence.UNCERTAIN, "Inertial fallback needs persistence");
        evidence.evaluate(1000, 0, Long.MAX_VALUE, false, false, false, true);
        check(evidence.evaluate(2000, 0, Long.MAX_VALUE, false, false, false, true)
                == MotionEvidencePolicy.Evidence.MOVING, "Sustained inertial gait survives GPS loss");
        check(evidence.evaluate(6000, 0, Long.MAX_VALUE, false, false, false, true)
                == MotionEvidencePolicy.Evidence.UNCERTAIN, "Evaluation gap is not sustained evidence");

        StepCounterEvidence counter = new StepCounterEvidence();
        check(counter.observe(0, 100, 0) == 0, "Cumulative baseline is not session steps");
        check(counter.observe(5000, 110, 14000) == 10, "Delayed batch preserves count");
        check(!counter.progressRecent(14000), "Delayed step is not current movement");
        check(counter.canAmendPendingStop(14000), "Supported delay may contradict pending stop");
        check(!counter.canAmendPendingStop(16000), "Ancient reports cannot alter current stop");
        check(counter.observe(4000, 999, 14000) == 0, "Out of order batch ignored");
        check(counter.observe(16000, 120, 15000) == 0, "Future batch ignored");
        check(counter.observe(15000, 175, 15000) == 65, "No arbitrary 50-step truncation");
        check(counter.progressRecent(15000), "Fresh counter evidence confirms movement");
        check(counter.observe(16000, 1, 16000) == 0 && !counter.progressRecent(16000), "Counter reset");
        check(counter.observe(17000, 3, 17000) == 2, "New counter baseline retained");
        counter.reset();
        check(counter.observe(18000, 80, 18000) == 0, "Session reset clears previous baseline");

        MotionTimeline delayed = new MotionTimeline();
        delayed.advance(0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        for (int i=1; i<=9; i++) delayed.advance(i*1000L,
                MotionEvidencePolicy.Evidence.STILL_MODERATE, MovementClassifier.Kind.UNKNOWN,
                StepCounterEvidence.STOP_CONFIRM_MS);
        check(delayed.stillMs() == 0, "Do not confirm before counter delivery budget");
        delayed.observeDelayedStep(5000);
        check(delayed.unknownMs() == 9000, "A delayed last step revokes overlapping stop only");
        for (int i=10; i<=20; i++) delayed.advance(i*1000L,
                MotionEvidencePolicy.Evidence.STILL_MODERATE, MovementClassifier.Kind.UNKNOWN,
                StepCounterEvidence.STOP_CONFIRM_MS);
        check(delayed.stillMs() == 0, "Renewed stop must meet full confirmation budget");
        delayed.advance(21000, MotionEvidencePolicy.Evidence.STILL_MODERATE,
                MovementClassifier.Kind.UNKNOWN, StepCounterEvidence.STOP_CONFIRM_MS);
        check(delayed.stillMs() == 12000, "Actual stop is confirmed and backdated");
        conserved(delayed, 21000);
        MotionTimeline nonoverlap = new MotionTimeline();
        nonoverlap.advance(10000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        nonoverlap.advance(11000, MotionEvidencePolicy.Evidence.STILL_MODERATE, MovementClassifier.Kind.UNKNOWN);
        nonoverlap.observeDelayedStep(9000);
        check(nonoverlap.unresolvedMs() == 1000, "Steps before the stop do not revoke it");
        conserved(nonoverlap, 1000);

        MotionTimeline stalledWorker = new MotionTimeline();
        stalledWorker.advance(0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        stalledWorker.advance(30000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.RUN);
        check(stalledWorker.unknownMs() == 30000 && stalledWorker.movingMs() == 0,
                "Worker blackout is not backfilled from the first new observation");
        conserved(stalledWorker, 30000);

        MotionTimeline briefStillUncertainty = new MotionTimeline();
        briefStillUncertainty.restore(0, 0, 0, 0);
        briefStillUncertainty.advance(0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        for (int i=1; i<=4; i++) briefStillUncertainty.advance(i*1000L,
                MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        briefStillUncertainty.advance(5000, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        briefStillUncertainty.advance(6000, MotionEvidencePolicy.Evidence.STILL_STRONG, MovementClassifier.Kind.UNKNOWN);
        briefStillUncertainty.advance(7000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        briefStillUncertainty.advance(8000, MotionEvidencePolicy.Evidence.MOVING, MovementClassifier.Kind.UNKNOWN);
        check(briefStillUncertainty.stillMs() == 6000 && briefStillUncertainty.movingUnknownMs() == 2000,
                "A recovered stationary interval cannot leak into later movement");
        conserved(briefStillUncertainty, 8000);

        // Exercise irregular callback timing and transitions, not only one-second examples.
        Random random = new Random(13);
        MotionTimeline mixed = new MotionTimeline();
        mixed.advance(0, MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        long now = 0;
        for (int i=0; i<2000; i++) {
            now += 1 + random.nextInt(1800);
            MotionEvidencePolicy.Evidence e = MotionEvidencePolicy.Evidence.values()[random.nextInt(4)];
            MovementClassifier.Kind k = MovementClassifier.Kind.values()[random.nextInt(5)];
            mixed.advance(now, e, k);
            mixed.advance(now, e, k); // Duplicate callbacks must not credit time twice.
        }
        conserved(mixed, now);
        System.out.println("PASS: " + checks + " motion regression checks.");
    }
}
