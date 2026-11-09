package it.example.passolibero;

import org.junit.Test;
import static org.junit.Assert.*;

/** Local JVM smoke checks; complement tests/run-core-tests.sh regression suite. */
public class CoreInvariantTest {
    @Test public void distanceAndPaceAreMetric() {
        assertEquals(111.19, RunMath.distanceMeters(0, 0, 0, 0.001), 0.5);
        assertEquals("5:00", RunMath.pace(300_000, 1_000));
    }

    @Test public void stationaryGpsDriftDoesNotIncreaseDistance() {
        DistanceAccumulator accumulator = new DistanceAccumulator();
        assertEquals(0, accumulator.accept(40, 14, 3, false), 0.0);
        assertEquals(0, accumulator.accept(40.00001, 14, 3, false), 0.0);
        assertEquals(0, accumulator.accept(40.00002, 14, 3, true), 0.0);
        assertTrue(accumulator.accept(40.0002, 14, 3, true) > 0);
    }

    @Test public void unknownGaitIsNotAttributedToWalkingOrRunning() {
        MotionTimeline timeline = new MotionTimeline();
        timeline.advance(0, MotionEvidencePolicy.Evidence.UNCERTAIN,
                MovementClassifier.Kind.UNKNOWN);
        for (int i = 1; i <= 4; i++) {
            timeline.advance(i * 1_000L, MotionEvidencePolicy.Evidence.MOVING,
                    MovementClassifier.Kind.UNKNOWN);
        }
        timeline.finish();
        assertEquals(0, timeline.runMs());
        assertEquals(0, timeline.walkMs());
        assertEquals(4_000, timeline.movingUnknownMs());
        assertEquals(timeline.runMs() + timeline.walkMs() + timeline.movingUnknownMs(),
                timeline.movingMs());
    }
}
