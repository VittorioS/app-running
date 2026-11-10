package it.example.passolibero;

import android.content.Context;
import android.content.SharedPreferences;

import it.example.passolibero.data.local.PreferencesSnapshotMapper;
import it.example.passolibero.domain.session.AdaptiveProfileSnapshot;

public final class AppPrefs {
    private static final String NAME = "passolibero";
    private AppPrefs() { }
    public static SharedPreferences get(Context c) { return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }
    public static double manualThreshold(SharedPreferences p) { return p.getInt("threshold_tenths", 60) / 10.0; }
    public static int intervalM(SharedPreferences p) { return p.getInt("interval_m", 1000); }
    public static boolean adaptiveEnabled(SharedPreferences p) { return p.getBoolean("adaptive", true); }
    public static int goalM(SharedPreferences p) { return p.getInt("goal_m", 0); }
    public static boolean voiceEnabled(SharedPreferences p) { return p.getBoolean("voice", true); }

    public static AdaptiveProfile loadProfile(SharedPreferences p) {
        AdaptiveProfileSnapshot snapshot = PreferencesSnapshotMapper.readProfile(p);
        AdaptiveProfile model = new AdaptiveProfile();
        model.walkSamples = snapshot.walkSamples;
        model.runSamples = snapshot.runSamples;
        model.walkMean = snapshot.walkMean;
        model.runMean = snapshot.runMean;
        return model;
    }

    public static void saveProfile(SharedPreferences p, AdaptiveProfile model) {
        PreferencesSnapshotMapper.saveProfile(
                p,
                new AdaptiveProfileSnapshot(
                        model.walkSamples, model.runSamples, model.walkMean, model.runMean));
    }
}
