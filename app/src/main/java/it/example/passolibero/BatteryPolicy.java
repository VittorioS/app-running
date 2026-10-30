package it.example.passolibero;

/** Pure scheduling rules for fewer UI writes; GPS remains at its configured rate. */
public final class BatteryPolicy {
    private BatteryPolicy() { }

    public static boolean shouldPublishState(long lastPublishedElapsed, long nowElapsed,
                                             boolean freshGps) {
        if (lastPublishedElapsed < 0 || nowElapsed < lastPublishedElapsed) return true;
        // Publish the new live speed promptly, but coalesce duplicate timer writes.
        long minimumIntervalMs = freshGps ? 700L : 2_000L;
        return nowElapsed - lastPublishedElapsed >= minimumIntervalMs;
    }
}
