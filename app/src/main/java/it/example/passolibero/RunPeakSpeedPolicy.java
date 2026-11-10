package it.example.passolibero;

/** Backward-compatible facade for the TrackingService API; logic lives in pure domain. */
public final class RunPeakSpeedPolicy {
    private RunPeakSpeedPolicy() { }

    public static double update(double currentMax, String movement, boolean moving,
                                boolean fresh, double accuracyM, double acceptedMeters,
                                double speedKmh) {
        return it.example.passolibero.domain.tracking.RunPeakSpeedPolicy.update(
                currentMax, movement, moving, fresh, accuracyM, acceptedMeters, speedKmh);
    }
}
