package it.example.passolibero.application.weather;

/** Cancellation for a single forecast check, including its notification and cooldown writes. */
public final class WeatherCheckCancellation {
    private volatile boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public synchronized void cancel() {
        cancelled = true;
    }

    public synchronized void runIfActive(Runnable action) {
        if (!cancelled) action.run();
    }
}
