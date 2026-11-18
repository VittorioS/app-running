package it.example.passolibero.domain.tracking;

/**
 * Pure effects when UNCERTAIN evidence interrupts an unconfirmed STOP.
 *
 * <p>The ledger owns UNKNOWN attribution, timestamps, and pending-stop resets.
 */
public final class UncertainStopInterruptionTransition {
    /** Proposed effects only; the caller mutates the ledger. */
    public record Effect(boolean interruptPendingStop, long unknownCreditMs) {}

    private UncertainStopInterruptionTransition() {}

    /** Preserve the legacy pending-stop guard and credit without normalization. */
    public static Effect evaluate(long stopSinceMs, long pendingStopMs) {
        boolean interrupted = stopSinceMs >= 0;
        return new Effect(interrupted, interrupted ? pendingStopMs : 0L);
    }
}
