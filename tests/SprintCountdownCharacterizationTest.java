import it.example.passolibero.SprintCountdownVoicePlan;

import java.util.Random;

/** Independent countdown oracle: captures legacy timing and phrase behavior before migration. */
public final class SprintCountdownCharacterizationTest {
    private static int assertions;

    private static void check(boolean ok, String reason) {
        assertions++;
        if (!ok) throw new AssertionError(reason);
    }

    private static void invalid(long startMs, int seconds) {
        boolean rejected = false;
        try {
            new SprintCountdownVoicePlan(startMs, seconds);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "invalid constructor must throw");
    }

    private static void verify(long startMs, int seconds, long[] sampleOffsets) {
        SprintCountdownVoicePlan plan = new SprintCountdownVoicePlan(startMs, seconds);
        check(plan.steps() == seconds + 1, "number of announcements");

        for (int step = 0; step <= seconds; step++) {
            String phrase = step == seconds ? "Via, sprint" : Integer.toString(seconds - step);
            long due = startMs + step * 1000L;
            check(phrase.equals(plan.phrase(step)), "exact original Italian phrase");
            check(due == plan.dueAt(step), "scheduled monotonic instant");
            for (long offset : sampleOffsets) {
                if ((offset < 0 && due < -offset) || (offset > 0 && due > Long.MAX_VALUE - offset))
                    continue;
                long now = due + offset;
                boolean stale = now < due || now - due > 650L;
                check(stale == plan.isStale(step, now), "legacy 650ms lateness gate");
            }
        }
        for (int step : new int[] {-1, seconds + 1, Integer.MAX_VALUE}) {
            boolean phraseRejected = false;
            boolean timeRejected = false;
            try { plan.phrase(step); } catch (IllegalArgumentException e) { phraseRejected = true; }
            try { plan.dueAt(step); } catch (IllegalArgumentException e) { timeRejected = true; }
            check(phraseRejected && timeRejected, "step must be rejected");
        }
    }

    public static void main(String[] args) {
        for (int seconds = 5; seconds <= 10; seconds++) {
            invalid(-1, seconds);
            invalid(0, 4);
            invalid(0, 11);
            invalid(Long.MAX_VALUE - seconds * 1000L + 1, seconds);
            for (long start : new long[] {0, 1, 123_000, 24 * 3600_000L,
                    Long.MAX_VALUE - seconds * 1000L}) {
                verify(start, seconds, new long[] {-1000, -1, 0, 1, 649, 650, 651, 700, 2000});
            }
        }

        Random rng = new Random(20020L);
        for (int i = 0; i < 3000; i++) {
            int seconds = 5 + rng.nextInt(6);
            long start = Math.floorMod(rng.nextLong(), Long.MAX_VALUE - 10_000L);
            verify(start, seconds, new long[] {-1, 0, 650, 651});
        }
        System.out.println("PASS: " + assertions + " independent countdown characterization assertions.");
    }
}
