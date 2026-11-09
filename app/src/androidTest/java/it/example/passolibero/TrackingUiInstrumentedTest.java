package it.example.passolibero;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

/** Android 11 UI/lifecycle smoke tests. They never start tracking or revoke device permissions. */
@RunWith(AndroidJUnit4.class)
public final class TrackingUiInstrumentedTest {
    private static void assertVisibleText(Activity activity, String expected) {
        ArrayList<View> matches = new ArrayList<>();
        activity.getWindow().getDecorView().findViewsWithText(
                matches, expected, View.FIND_VIEWS_WITH_TEXT);
        boolean found = false;
        for (View match : matches) {
            if (match instanceof TextView
                    && ((TextView) match).getText().toString().contains(expected)) {
                found = true;
                break;
            }
        }
        assertTrue("Missing text on screen: " + expected, found);
    }

    @Test public void homeSurvivesActivityRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());
                assertVisibleText(activity, "PassoLibero");
                assertVisibleText(activity, "Inizia sessione");
                assertVisibleText(activity, "Storico delle sessioni");
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());
                assertVisibleText(activity, "PassoLibero");
            });
        }
    }

    @Test public void activeSessionScreenOpensWithoutStartingSensors() {
        // Opening the session UI itself does not issue a TrackingService.START.
        try (ActivityScenario<ActiveSessionActivity> scenario =
                     ActivityScenario.launch(ActiveSessionActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());
                assertVisibleText(activity, "Sessione in corso");
                assertVisibleText(activity, "Corsa");
                assertVisibleText(activity, "Cammina");
            });
        }
    }

    @Test public void historySurvivesActivityRecreation() {
        try (ActivityScenario<HistoryActivity> scenario =
                     ActivityScenario.launch(HistoryActivity.class)) {
            scenario.onActivity(activity -> assertVisibleText(activity, "Storico allenamenti"));
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());
                assertVisibleText(activity, "Storico allenamenti");
            });
        }
    }

    @Test public void permissionContractAndOptionalSensorsDoNotRequireHardware() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager pm = app.getPackageManager();
        PackageInfo info = pm.getPackageInfo(app.getPackageName(),
                PackageManager.GET_PERMISSIONS | PackageManager.GET_CONFIGURATIONS);
        assertNotNull(info.requestedPermissions);
        assertTrue(Arrays.asList(info.requestedPermissions)
                .contains(Manifest.permission.ACCESS_FINE_LOCATION));
        assertTrue(Arrays.asList(info.requestedPermissions)
                .contains(Manifest.permission.ACTIVITY_RECOGNITION));
        assertTrue(Arrays.asList(info.requestedPermissions)
                .contains(Manifest.permission.FOREGROUND_SERVICE));
        if (info.reqFeatures != null) {
            for (android.content.pm.FeatureInfo feature : info.reqFeatures) {
                if (PackageManager.FEATURE_LOCATION_GPS.equals(feature.name)
                        || PackageManager.FEATURE_SENSOR_STEP_DETECTOR.equals(feature.name)) {
                    assertEquals("GPS and step detector must remain optional",
                            0, feature.flags & android.content.pm.FeatureInfo.FLAG_REQUIRED);
                }
            }
        }

        // Inspect actual permission state; do NOT grant/revoke permissions in tests.
        // Changing dangerous permissions can kill the app process and alter real devices.
        int fineBefore = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        int stepsBefore = app.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION);
        try (ActivityScenario<HistoryActivity> scenario =
                     ActivityScenario.launch(HistoryActivity.class)) {
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
        assertEquals("Opening history must not mutate location authorization",
                fineBefore, app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION));
        assertEquals("Opening history must not mutate motion authorization",
                stepsBefore, app.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION));
    }
}
