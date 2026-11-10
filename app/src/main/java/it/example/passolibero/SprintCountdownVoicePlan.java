package it.example.passolibero;

/**
 * Backward-compatible facade for the sprint countdown API used by TrackingService.
 * All timing and phrase rules live in the Android-free domain.
 */
public final class SprintCountdownVoicePlan {
    private final it.example.passolibero.domain.sprint.SprintCountdownVoicePlan delegate;

    public SprintCountdownVoicePlan(long startElapsedMs, int seconds) {
        delegate = new it.example.passolibero.domain.sprint.SprintCountdownVoicePlan(
                startElapsedMs, seconds);
    }

    public int steps() { return delegate.steps(); }

    public String phrase(int step) { return delegate.phrase(step); }

    public long dueAt(int step) { return delegate.dueAt(step); }

    public boolean isStale(int step, long now) { return delegate.isStale(step, now); }
}
