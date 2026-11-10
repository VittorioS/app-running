package it.example.passolibero.domain.sprint;

/**
 * Deterministic sprint countdown based on a monotonic clock, independent of GPS and TTS.
 *
 * <p>For a 5–10 second countdown, announce each number once a second, then "Via, sprint" at the
 * final deadline. Discard missed announcements more than 650 ms late.
 */
public final class SprintCountdownVoicePlan {
    private final long startElapsedMs;
    private final int seconds;

    public SprintCountdownVoicePlan(long startElapsedMs, int seconds) {
        if (startElapsedMs < 0
                || seconds < 5
                || seconds > 10
                || startElapsedMs > Long.MAX_VALUE - seconds * 1000L) {
            throw new IllegalArgumentException("Countdown sprint non valido");
        }
        this.startElapsedMs = startElapsedMs;
        this.seconds = seconds;
    }

    public int steps() {
        return seconds + 1;
    }

    /** Step zero announces the first number; the last step announces sprint start. */
    public String phrase(int step) {
        if (step < 0 || step > seconds) {
            throw new IllegalArgumentException("Indice countdown non valido");
        }
        return step == seconds ? "Via, sprint" : Integer.toString(seconds - step);
    }

    public long dueAt(int step) {
        if (step < 0 || step > seconds) {
            throw new IllegalArgumentException("Indice countdown non valido");
        }
        return startElapsedMs + step * 1000L;
    }

    public boolean isStale(int step, long now) {
        return now < dueAt(step) || now - dueAt(step) > 650L;
    }
}
