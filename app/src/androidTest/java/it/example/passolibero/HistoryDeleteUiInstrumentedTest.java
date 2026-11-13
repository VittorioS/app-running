package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.UiAutomation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * ADR-002/#28: exercise the real HistoryActivity delete controls on a disposable API 30 emulator.
 *
 * <p>The only rows created or deleted are synthetic test sessions. No real handset, user
 * history, GPS hardware, runtime permissions, or signing material are touched.
 */
@RunWith(AndroidJUnit4.class)
public final class HistoryDeleteUiInstrumentedTest {
    private static final long ACTIVE_WALL = 4102444800000L;
    private static final long DELETE_WALL = 4105036800000L;

    private static void requireDisposableEmulator() {
        assumeTrue("Android 11/API 30 only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Never interact with a person's stored history",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    private static Button deleteButtonFor(View view, String description) {
        if (view instanceof Button
                && description.equals(String.valueOf(view.getContentDescription()))) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) view;
            for (int index = 0; index < container.getChildCount(); index++) {
                Button button = deleteButtonFor(container.getChildAt(index), description);
                if (button != null) return button;
            }
        }
        return null;
    }

    private static Button deleteButtonFor(Activity activity, long startedWallMs) {
        String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(startedWallMs));
        return deleteButtonFor(
                activity.getWindow().getDecorView(), "Elimina la sessione del " + date);
    }

    private static void clickVisibleDialogAction(String label) {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        long deadline = SystemClock.uptimeMillis() + 7_000L;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(label);
                for (AccessibilityNodeInfo item : matches) {
                    if (label.contentEquals(item.getText())
                            && item.isClickable()
                            && item.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                        return;
                    }
                }
            }
            SystemClock.sleep(75);
        }
        throw new AssertionError("Missing clickable confirmation dialog action: " + label);
    }

    @Test
    public void activeSessionDeleteButtonStaysDisabledAfterActivityRecreation() {
        requireDisposableEmulator();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = AppPrefs.get(context);
        assertFalse("Disposable emulator must have no active session",
                prefs.getBoolean("active", false));
        boolean hadActive = prefs.contains("active");
        boolean hadId = prefs.contains("active_id");
        long originalId = hadId ? prefs.getLong("active_id", 0L) : 0L;
        boolean originalDatabase = context.getDatabasePath("sessions.db").exists();

        try (TrackerDb database = new TrackerDb(context)) {
            long sessionId = database.newSession(ACTIVE_WALL);
            try {
                assertTrue(prefs.edit().putBoolean("active", true)
                        .putLong("active_id", sessionId).commit());
                try (ActivityScenario<HistoryActivity> scenario =
                        ActivityScenario.launch(HistoryActivity.class)) {
                    scenario.onActivity(activity -> {
                        Button button = deleteButtonFor(activity, ACTIVE_WALL);
                        assertNotNull("Synthetic active row must appear in history", button);
                        assertEquals("Sessione attiva: non eliminabile",
                                button.getText().toString());
                        assertFalse("Active session must not be deletable", button.isEnabled());
                    });
                    scenario.recreate();
                    scenario.onActivity(activity -> {
                        Button button = deleteButtonFor(activity, ACTIVE_WALL);
                        assertNotNull("Active row must survive recreation", button);
                        assertFalse("Active row must remain protected", button.isEnabled());
                    });
                }
                assertNotNull("Disabling the button must not delete the row",
                        database.session(sessionId));
            } finally {
                // Restore original flags even on failed assertions.
                SharedPreferences.Editor restore = prefs.edit();
                if (hadActive) restore.putBoolean("active", false);
                else restore.remove("active");
                if (hadId) restore.putLong("active_id", originalId);
                else restore.remove("active_id");
                assertTrue("Restore original preferences", restore.commit());
                if (database.session(sessionId) != null) {
                    assertTrue("Remove only the test fixture",
                            database.deleteSession(sessionId));
                }
            }
        }
        if (!originalDatabase) {
            assertTrue("Remove the test-only SQLite database",
                    context.deleteDatabase("sessions.db"));
        }
    }

    @Test
    public void cancelKeepsSessionAndConfirmedDeletionRemovesOnlyFixture() {
        requireDisposableEmulator();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = AppPrefs.get(context);
        assertFalse("Disposable emulator must have no active session",
                prefs.getBoolean("active", false));
        boolean originalDatabase = context.getDatabasePath("sessions.db").exists();

        try (TrackerDb database = new TrackerDb(context)) {
            long sessionId = database.newSession(DELETE_WALL);
            try {
                database.point(sessionId, DELETE_WALL + 1_000L,
                        40.0, 14.0, 4.0, 8.0, 3.0, "RUN");
                assertEquals("Fixture must be the most recent row",
                        sessionId, database.recent(1).get(0).id);

                try (ActivityScenario<HistoryActivity> scenario =
                        ActivityScenario.launch(HistoryActivity.class)) {
                    scenario.onActivity(activity -> {
                        Button button = deleteButtonFor(activity, DELETE_WALL);
                        assertNotNull("Synthetic row must have a delete control", button);
                        assertTrue(button.isEnabled());
                        assertTrue(button.performClick());
                    });
                    clickVisibleDialogAction("Annulla");
                    assertNotNull("Cancel must retain history", database.session(sessionId));
                    assertEquals(1, database.pointsAfter(sessionId, 0, 10).size());

                    scenario.onActivity(activity -> {
                        Button button = deleteButtonFor(activity, DELETE_WALL);
                        assertNotNull("Cancel must return to the existing row", button);
                        assertTrue(button.performClick());
                    });
                    clickVisibleDialogAction("Elimina");

                    long deadline = SystemClock.uptimeMillis() + 8_000L;
                    while (database.session(sessionId) != null
                            && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(75);
                    }
                    assertNull("Confirmation must delete the selected session",
                            database.session(sessionId));
                    assertTrue("Associated route points must be removed",
                            database.pointsAfter(sessionId, 0, 10).isEmpty());
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    scenario.onActivity(activity ->
                            assertNull("Deleted row must disappear from history",
                                    deleteButtonFor(activity, DELETE_WALL)));
                }
            } finally {
                // If the UI path fails, delete only this synthetic row.
                if (database.session(sessionId) != null) {
                    assertTrue(database.deleteSession(sessionId));
                }
            }
        }
        if (!originalDatabase) {
            assertTrue("Remove the test-only SQLite database",
                    context.deleteDatabase("sessions.db"));
        }
    }
}
