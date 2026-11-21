package it.example.passolibero.domain.tracking;

/**
 * Pure transition result for a callback with insufficient motion evidence.
 *
 * <p>The legacy ledger owns callback ordering, pending STOP cancellation, all counter mutations and
 * the actual committed/visible state. This value describes only the effects to apply.
 */
public final class UncertainEvidenceTransition {
    /** The window anchor and existing grace-expiry effects, without ledger mutations. */
    public record WindowEffect(
            long uncertainSinceMsAfter,
            long unresolvedMsAfter,
            long unknownCreditMs,
            boolean invalidateConfirmedState) {}

    private UncertainEvidenceTransition() {}

    /**
     * Preserve the first UNCERTAIN callback's previous-interval anchor and subsequent expiry.
     *
     * <p>The old sentinel is negative; the first accepted interval starts at nowMs - deltaMs.
     * Existing windows retain their original anchor, including after grace expiry.
     */
    public static WindowEffect resolveWindow(
            long nowMs, long deltaMs, long uncertainSinceMs, long unresolvedMs) {
        long anchorMs = uncertainSinceMs < 0 ? nowMs - deltaMs : uncertainSinceMs;
        Effect grace = resolve(nowMs - anchorMs, unresolvedMs, deltaMs);
        return new WindowEffect(
                anchorMs,
                grace.unresolvedMsAfter(),
                grace.unknownCreditMs(),
                grace.invalidateConfirmedState());
    }

    /** Immutable credit and state-invalidation instructions; no side effects are performed here. */
    public record Effect(
            long unresolvedMsAfter, long unknownCreditMs, boolean invalidateConfirmedState) {}

    /**
     * Keeps the current interval pending below the grace boundary, otherwise settles all pending
     * uncertainty as UNKNOWN and invalidates the previously confirmed state.
     */
    public static Effect resolve(long uncertainElapsedMs, long unresolvedMs, long deltaMs) {
        long totalMs = unresolvedMs + deltaMs;
        if (UncertaintyGracePolicy.shouldKeepPending(uncertainElapsedMs)) {
            return new Effect(totalMs, 0L, false);
        }
        return new Effect(0L, totalMs, true);
    }
}
