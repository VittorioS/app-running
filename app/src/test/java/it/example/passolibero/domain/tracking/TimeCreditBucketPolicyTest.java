package it.example.passolibero.domain.tracking;

import static org.junit.Assert.assertEquals;

import it.example.passolibero.MovementClassifier;
import org.junit.Test;

/** P4b/#25: exact parity with the original MotionTimeline.credit() destination decisions. */
public final class TimeCreditBucketPolicyTest {
    @Test
    public void matchesLegacyDestinationForEveryCommittedState() {
        for (MovementClassifier.Kind kind : MovementClassifier.Kind.values()) {
            TimeCreditBucketPolicy.Bucket expected;
            if (kind == MovementClassifier.Kind.RUN) {
                expected = TimeCreditBucketPolicy.Bucket.RUN;
            } else if (kind == MovementClassifier.Kind.WALK) {
                expected = TimeCreditBucketPolicy.Bucket.WALK;
            } else if (kind == MovementClassifier.Kind.STILL) {
                expected = TimeCreditBucketPolicy.Bucket.STILL;
            } else if (kind == MovementClassifier.Kind.MOVING_UNKNOWN) {
                expected = TimeCreditBucketPolicy.Bucket.MOVING_UNKNOWN;
            } else {
                expected = TimeCreditBucketPolicy.Bucket.UNKNOWN;
            }
            assertEquals(kind.name(), expected, TimeCreditBucketPolicy.bucketFor(kind.name()));
        }
    }

    @Test
    public void nullAndUnrecognizedStatesStayUnknown() {
        assertEquals(TimeCreditBucketPolicy.Bucket.UNKNOWN, TimeCreditBucketPolicy.bucketFor(null));
        assertEquals(TimeCreditBucketPolicy.Bucket.UNKNOWN, TimeCreditBucketPolicy.bucketFor(""));
        assertEquals(
                TimeCreditBucketPolicy.Bucket.UNKNOWN, TimeCreditBucketPolicy.bucketFor("running"));
        assertEquals(
                TimeCreditBucketPolicy.Bucket.UNKNOWN, TimeCreditBucketPolicy.bucketFor("MOVING"));
        assertEquals(
                TimeCreditBucketPolicy.Bucket.UNKNOWN, TimeCreditBucketPolicy.bucketFor("run"));
    }

    @Test
    public void eachCallUsesOnlyCurrentState() {
        assertEquals(TimeCreditBucketPolicy.Bucket.RUN, TimeCreditBucketPolicy.bucketFor("RUN"));
        assertEquals(
                TimeCreditBucketPolicy.Bucket.UNKNOWN, TimeCreditBucketPolicy.bucketFor("UNKNOWN"));
        assertEquals(TimeCreditBucketPolicy.Bucket.WALK, TimeCreditBucketPolicy.bucketFor("WALK"));
        assertEquals(
                TimeCreditBucketPolicy.Bucket.MOVING_UNKNOWN,
                TimeCreditBucketPolicy.bucketFor("MOVING_UNKNOWN"));
        assertEquals(
                TimeCreditBucketPolicy.Bucket.STILL, TimeCreditBucketPolicy.bucketFor("STILL"));
    }
}
