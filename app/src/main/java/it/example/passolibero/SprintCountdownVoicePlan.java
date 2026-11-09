package it.example.passolibero;

/**
 * Sequenza di annunci dello sprint su un clock monotono.
 * Dieci secondi: 10, 9, ..., 1 e "Via, sprint" esattamente al termine.
 * I tempi non dipendono dai campioni GPS.
 */
public final class SprintCountdownVoicePlan {
    private final long startElapsedMs;
    private final int seconds;

    public SprintCountdownVoicePlan(long startElapsedMs, int seconds) {
        if (startElapsedMs < 0 || seconds < 5 || seconds > 10
                || startElapsedMs > Long.MAX_VALUE - seconds * 1000L)
            throw new IllegalArgumentException("Countdown sprint non valido");
        this.startElapsedMs = startElapsedMs;
        this.seconds = seconds;
    }

    public int steps() {
        return seconds + 1;
    }

    /** Step zero: numero iniziale; step finale: partenza. */
    public String phrase(int step) {
        if (step < 0 || step > seconds)
            throw new IllegalArgumentException("Indice countdown non valido");
        return step == seconds ? "Via, sprint" : Integer.toString(seconds - step);
    }

    public long dueAt(int step) {
        if (step < 0 || step > seconds)
            throw new IllegalArgumentException("Indice countdown non valido");
        return startElapsedMs + step * 1000L;
    }

    public boolean isStale(int step, long now) {
        return now < dueAt(step) || now - dueAt(step) > 650L;
    }
}
