package it.example.passolibero.application.tracking;

import it.example.passolibero.domain.ForegroundStartPolicy;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Coordinates the existing foreground START attempt without owning Android side effects.
 *
 * <p>The platform Service supplies lazy permission/GPS checks and effects. Permission denial
 * happens before foreground acquisition; successful foreground acquisition happens
 * synchronously before the initialization task is posted. Only RuntimeException from
 * foreground acquisition is handled here, as in the legacy Service.
 */
public final class TrackingStartupCoordinator {
    private TrackingStartupCoordinator() {}

    public enum StartResult {
        STARTED,
        NOT_STARTED
    }

    /**
     * Orchestrates the legacy start branch with unchanged read and effect ordering.
     *
     * <p>Each callback is invoked lazily at its original point. Errors from permission checks,
     * denial handling, and queuing work are not swallowed.
     */
    public static StartResult start(
            BooleanSupplier fineLocationGranted,
            BooleanSupplier gpsProviderEnabled,
            Runnable onUnavailable,
            Runnable acquireForeground,
            Consumer<RuntimeException> onForegroundFailure,
            Runnable enqueueInitialization) {
        if (!ForegroundStartPolicy.canStart(fineLocationGranted, gpsProviderEnabled)) {
            onUnavailable.run();
            return StartResult.NOT_STARTED;
        }

        try {
            acquireForeground.run();
        } catch (RuntimeException failure) {
            onForegroundFailure.accept(failure);
            return StartResult.NOT_STARTED;
        }

        enqueueInitialization.run();
        return StartResult.STARTED;
    }
}
