package it.example.passolibero;

import android.app.Application;
import android.os.Build;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Smoke tests run on an Android 11 (API 30) emulator in pull-request CI. */
@RunWith(AndroidJUnit4.class)
public class Android11CompatibilityTest {
    @Test public void installedOnAndroid11() {
        assertEquals("The CI emulator must run API 30", 30, Build.VERSION.SDK_INT);
        Application app = (Application) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assertEquals("it.example.passolibero", app.getPackageName());
        assertTrue(app.getApplicationInfo().minSdkVersion <= 30);
    }

    @Test public void homeOpens() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    @Test public void historyOpens() {
        try (ActivityScenario<HistoryActivity> scenario =
                     ActivityScenario.launch(HistoryActivity.class)) {
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }
}
