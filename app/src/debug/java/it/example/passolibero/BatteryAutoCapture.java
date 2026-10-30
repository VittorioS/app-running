package it.example.passolibero;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Debug only: one optional 10-minute sample after a 60-second GPS warmup. */
public final class BatteryAutoCapture {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable scheduled;
    private static long currentSession = -1;

    private BatteryAutoCapture() { }

    public static void onSessionStart(Context context, long id) {
        Context app = context.getApplicationContext();
        if (!AppPrefs.get(app).getBoolean("battery_auto_debug", false)) return;
        if (AppPrefs.get(app).getLong("battery_auto_session_id", -1) == id) return;
        AppPrefs.get(app).edit().putLong("battery_auto_session_id", id).apply();
        clearPending();
        currentSession = id;
        scheduled = () -> {
            scheduled = null;
            if (!AppPrefs.get(app).getBoolean("battery_auto_debug", false)
                    || !AppPrefs.get(app).getBoolean("active", false)
                    || AppPrefs.get(app).getLong("active_id", 0) != id) return;
            if (!BatteryRecorder.isRunning()) {
                BatteryRecorder.start(app, 600, "AUTO - sessione attiva dopo 60 secondi");
            }
        };
        MAIN.postDelayed(scheduled, 60_000L);
    }

    public static void onSessionStop(Context context, long id) {
        if (currentSession == id) {
            clearPending();
            currentSession = -1;
        }
        if (BatteryRecorder.isRunning()) BatteryRecorder.stop();
    }

    public static void disable(Context context) {
        clearPending();
        if (BatteryRecorder.isRunning()) BatteryRecorder.stop();
    }

    private static void clearPending() {
        if (scheduled != null) MAIN.removeCallbacks(scheduled);
        scheduled = null;
    }
}
