import it.example.passolibero.domain.SessionRecoveryPolicy;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** Executable reference-equivalence tests for the original service formulas. */
public final class SessionRecoveryPolicyTest {
    private static int checks;

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    private static long legacyStart(
            long startedElapsed, long startedWall, long[] elapsedReads, long nowWall,
            AtomicInteger count) {
        // Intentional independent copy of the pre-extraction conditional.
        if (startedElapsed <= 0 || startedElapsed > elapsedReads[count.getAndIncrement()]) {
            long elapsedBasedOnWall = Math.max(0L, nowWall - startedWall);
            return Math.max(0L, elapsedReads[count.getAndIncrement()] - elapsedBasedOnWall);
        }
        return startedElapsed;
    }

    private static long originalGap(
            boolean restore, long startedElapsed, long lastTick,
            long run, long walk, long movingUnknown, long still, long unknown) {
        long accounted = run + walk + movingUnknown + still + unknown;
        long elapsed = Math.max(0L, lastTick - startedElapsed);
        return restore && elapsed > accounted ? elapsed - accounted : 0L;
    }

    private static void clockEquivalence(
            long startedElapsed, long startedWall, long firstElapsed,
            long secondElapsed, long nowWall) {
        long[] timestamps = {firstElapsed, secondElapsed};
        AtomicInteger calls = new AtomicInteger();
        LongSupplier elapsed = () -> timestamps[calls.getAndIncrement()];
        AtomicInteger wallCalls = new AtomicInteger();
        LongSupplier wall = () -> {
            wallCalls.incrementAndGet();
            return nowWall;
        };
        long actual = SessionRecoveryPolicy.recoverStartedElapsedMs(
                startedElapsed, startedWall, elapsed, wall);
        AtomicInteger legacyCalls = new AtomicInteger();
        long legacy = legacyStart(
                startedElapsed, startedWall, timestamps, nowWall, legacyCalls);
        check(actual == legacy, "Recovery must equal the legacy service formula");
        check(calls.get() == legacyCalls.get(), "Clock read count must be unchanged");
        check(wallCalls.get() == (legacyCalls.get() == 2
                        || startedElapsed <= 0 ? 1 : 0),
                "Wall clock must be read only during fallback");
    }

    public static void main(String[] args) {
        clockEquivalence(1000L, 100000L, 1200L, 1201L, 100500L);
        clockEquivalence(0L, 100000L, 4000L, 4001L, 200000L);
        clockEquivalence(9000L, 100000L, 4000L, 4001L, 100500L);
        clockEquivalence(-1L, 100000L, 4000L, 4001L, 99999L);
        clockEquivalence(10L, 1000L, 10L, 11L, 1000L);

        // Deterministic property probes over plausible persisted session times.
        Random random = new Random(6006);
        for (int i = 0; i < 1000; i++) {
            long now = 1000L + random.nextInt(1_000_000);
            long started = random.nextInt(1_100_000) - 10_000L;
            long wallStarted = 1_000_000L + random.nextInt(900_000);
            long nowWall = 1_000_000L + random.nextInt(900_000);
            clockEquivalence(started, wallStarted, now, now + 1, nowWall);

            boolean recovering = random.nextBoolean();
            long run = random.nextInt(50_000);
            long walk = random.nextInt(50_000);
            long movingUnknown = random.nextInt(50_000);
            long still = random.nextInt(50_000);
            long unknown = random.nextInt(50_000);
            long current = now + random.nextInt(200_000);
            long expected = originalGap(
                    recovering, started, current, run, walk, movingUnknown, still, unknown);
            long actual = SessionRecoveryPolicy.unobservedUnknownMs(
                    recovering, started, current, run, walk, movingUnknown, still, unknown);
            check(expected == actual, "Unknown recovery exactly matches previous arithmetic");
            check(actual >= 0, "No negative time after restoration");
        }

        // A persisted 20s RUN + 10s WALK, recovered 45s later: only missing
        // 15s becomes UNKNOWN. No other category is reassigned by this policy.
        long recovered = SessionRecoveryPolicy.unobservedUnknownMs(
                true, 1000, 46000, 20000, 10000, 0, 0, 0);
        check(recovered == 15000, "Unobserved worker gap is UNKNOWN");
        check(SessionRecoveryPolicy.unobservedUnknownMs(
                false, 1000, 46000, 20000, 10000, 0, 0, 0) == 0,
                "Brand-new session never retroactively adds unknown time");
        check(SessionRecoveryPolicy.unobservedUnknownMs(
                true, 1000, 25000, 20000, 10000, 0, 0, 0) == 0,
                "Already accounted time is never counted twice");
        System.out.println("PASS: " + checks + " recovery equivalence assertions.");
    }
}
