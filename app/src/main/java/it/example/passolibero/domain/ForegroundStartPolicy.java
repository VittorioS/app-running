package it.example.passolibero.domain;

import java.util.function.BooleanSupplier;

/**
 * Android-free guard decisions for the explicit foreground tracking lifecycle. The platform
 * supplies permission/GPS checks and SDK-specific service type flags.
 */
public final class ForegroundStartPolicy {
    private ForegroundStartPolicy() {}

    /**
     * Evaluate GPS availability only when fine location permission is granted. This preserves the
     * service's original short-circuit evaluation order.
     */
    public static boolean canStart(
            BooleanSupplier fineLocationGranted, BooleanSupplier gpsProviderEnabled) {
        return fineLocationGranted.getAsBoolean() && gpsProviderEnabled.getAsBoolean();
    }

    /**
     * Android 11 uses the location type only. The optional health type is read on API 34+ only when
     * physical activity recognition is permitted.
     */
    public static int foregroundServiceType(
            int apiLevel, int locationType, int healthType, BooleanSupplier activityGranted) {
        int type = locationType;
        if (apiLevel >= 34 && activityGranted.getAsBoolean()) {
            type |= healthType;
        }
        return type;
    }
}
