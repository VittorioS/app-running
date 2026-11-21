package it.example.passolibero.platform.location;

import android.location.LocationListener;
import android.location.LocationManager;
import android.os.HandlerThread;

/**
 * Android boundary for replacing the active GPS request on the existing tracking worker.
 *
 * <p>The caller still owns permission failures, fast-to-normal retries, and session state.
 */
public final class GpsUpdatesRegistration {
    /** Injectable operations for deterministic order/failure tests without registering GPS. */
    public interface Calls {
        void removeUpdates();

        void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM);
    }

    private GpsUpdatesRegistration() {}

    /** Preserve the remove-then-request order and the exact legacy 200/1000 ms frequencies. */
    public static void replace(Calls calls, boolean fast) {
        calls.removeUpdates();
        calls.requestLocationUpdates(LocationManager.GPS_PROVIDER, fast ? 200L : 1_000L, 0f);
    }

    /** Use the original listener and worker thread; retrieve its looper after removal. */
    public static void replace(
            LocationManager manager,
            LocationListener listener,
            HandlerThread workerThread,
            boolean fast) {
        replace(
                new Calls() {
                    @Override
                    public void removeUpdates() {
                        manager.removeUpdates(listener);
                    }

                    @Override
                    public void requestLocationUpdates(
                            String provider, long minTimeMs, float minDistanceM) {
                        manager.requestLocationUpdates(
                                provider,
                                minTimeMs,
                                minDistanceM,
                                listener,
                                workerThread.getLooper());
                    }
                },
                fast);
    }
}
