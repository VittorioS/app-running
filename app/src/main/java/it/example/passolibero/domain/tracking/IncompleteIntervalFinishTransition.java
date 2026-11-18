package it.example.passolibero.domain.tracking;

/**
 * Pure credits for intervals left unresolved at session end or a worker blackout.
 *
 * <p>Unconfirmed STOP and unresolved evidence become UNKNOWN, not retroactive movement or
 * stillness. The legacy ledger owns counters, timestamps and resets.
 */
public final class IncompleteIntervalFinishTransition {
    /** Separate credits retain the original order of ledger additions. */
    public record Effect(long pendingStopUnknownMs, long unresolvedUnknownMs) {}

    private IncompleteIntervalFinishTransition() {}

    /** Return both pending durations unchanged, including Java long boundary semantics. */
    public static Effect evaluate(long stopPendingMs, long unresolvedMs) {
        return new Effect(stopPendingMs, unresolvedMs);
    }
}
