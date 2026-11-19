package it.example.passolibero.domain.tracking;

/**
 * Pure admission of the monotonic timestamp of a tracking callback.
 *
 * <p>The first callback anchors the ledger. Duplicate and stale callbacks are ignored; newer
 * callbacks advance its clock. Gap classification and all counter changes remain in the ledger.
 */
public final class TimelineClockTransition {
    public enum Action {
        INITIALIZE,
        IGNORE,
        ADVANCE
    }

    /** Clock instructions only; no evidence, state, or ledger counters are mutated here. */
    public record Effect(Action action, long lastMsAfter, long deltaMs) {}

    private TimelineClockTransition() {}

    /** Preserve the legacy negative timestamp sentinel and strict ordering guard. */
    public static Effect evaluate(long lastMs, long nowMs) {
        if (lastMs < 0) {
            return new Effect(Action.INITIALIZE, nowMs, 0L);
        }
        if (nowMs <= lastMs) {
            return new Effect(Action.IGNORE, lastMs, 0L);
        }
        return new Effect(Action.ADVANCE, nowMs, nowMs - lastMs);
    }
}
