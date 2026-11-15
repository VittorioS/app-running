package it.example.passolibero.application.tracking;

/**
 * One P4b transition: entering a confirmed stop or UNKNOWN invalidates previously inferred gait.
 *
 * <p>Inputs are visible movement names supplied by the legacy Service boundary. The decision is
 * pure; the Service owns the ActivityEvidence reset side effect, in its existing callback order.
 */
public final class TrackingGaitTransition {
    private TrackingGaitTransition() {}

    public static boolean shouldInvalidateGait(String previousMovement, String currentMovement) {
        return !previousMovement.equals(currentMovement)
                && ("STILL".equals(currentMovement) || "UNKNOWN".equals(currentMovement));
    }
}
