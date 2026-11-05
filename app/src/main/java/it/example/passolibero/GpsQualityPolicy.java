package it.example.passolibero;

/** Pure GPS confidence thresholds shared by the startup check and tracking. */
public final class GpsQualityPolicy {
    public static final float MAX_ACCURACY_METERS = 25f;
    public static final long MAX_FIX_AGE_MS = 10_000L;
    public static final long POOR_CONFIRM_MS = 3_000L;
    public static final long RECOVERY_CONFIRM_MS = 10_000L;

    private GpsQualityPolicy() { }

    public static boolean isReliable(float accuracyM, long ageMs) {
        return Float.isFinite(accuracyM) && accuracyM > 0
                && accuracyM <= MAX_ACCURACY_METERS
                && ageMs >= 0 && ageMs <= MAX_FIX_AGE_MS;
    }
    public static boolean confirmedPoor(long poorSinceMs, long nowMs) {
        return poorSinceMs >= 0 && nowMs - poorSinceMs >= POOR_CONFIRM_MS;
    }
    public static boolean confirmedRecovery(long goodSinceMs, long nowMs) {
        return goodSinceMs >= 0 && nowMs - goodSinceMs >= RECOVERY_CONFIRM_MS;
    }
}
