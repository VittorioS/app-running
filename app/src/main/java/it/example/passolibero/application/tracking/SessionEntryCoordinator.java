package it.example.passolibero.application.tracking;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * First P4a seam: decide whether persisted session state should be loaded at entry.
 *
 * <p>Suppliers preserve the short-circuit preference reads in the legacy service. Android
 * foreground acquisition, worker ordering, effects and persistence remain outside this class.
 */
public final class SessionEntryCoordinator {
    private SessionEntryCoordinator() {}

    /** Resuming requires both the active marker and a strictly positive persisted session ID. */
    public static boolean shouldRestore(
            BooleanSupplier activeFlag, LongSupplier persistedSessionId) {
        return activeFlag.getAsBoolean() && persistedSessionId.getAsLong() > 0;
    }

    /** Early STOP reads saved state whenever active is true, even if the saved ID is invalid. */
    public static boolean shouldHydrateForEarlyStop(BooleanSupplier activeFlag) {
        return activeFlag.getAsBoolean();
    }
}
