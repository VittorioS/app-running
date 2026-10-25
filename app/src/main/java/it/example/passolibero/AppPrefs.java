package it.example.passolibero;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String NAME = "passolibero";
    private AppPrefs() { }
    public static SharedPreferences get(Context c) { return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }
    public static double manualThreshold(SharedPreferences p) { return p.getInt("threshold_tenths", 60) / 10.0; }
    public static int intervalM(SharedPreferences p) { return p.getInt("interval_m", 1000); }
    public static boolean adaptiveEnabled(SharedPreferences p) { return p.getBoolean("adaptive", true); }
    public static boolean voiceEnabled(SharedPreferences p) { return p.getBoolean("voice", true); }

    public static AdaptiveProfile loadProfile(SharedPreferences p) {
        AdaptiveProfile model = new AdaptiveProfile();
        model.walkSamples = p.getInt("model_walk_n", 0);
        model.runSamples = p.getInt("model_run_n", 0);
        model.walkMean = Double.longBitsToDouble(p.getLong("model_walk_bits", Double.doubleToLongBits(0)));
        model.runMean = Double.longBitsToDouble(p.getLong("model_run_bits", Double.doubleToLongBits(0)));
        return model;
    }

    public static void saveProfile(SharedPreferences p, AdaptiveProfile model) {
        p.edit().putInt("model_walk_n", model.walkSamples).putInt("model_run_n", model.runSamples)
                .putLong("model_walk_bits", Double.doubleToLongBits(model.walkMean))
                .putLong("model_run_bits", Double.doubleToLongBits(model.runMean)).apply();
    }
}
