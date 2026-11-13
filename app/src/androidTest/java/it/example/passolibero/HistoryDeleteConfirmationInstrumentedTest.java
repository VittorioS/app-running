package it.example.passolibero;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.action.ViewActions.click;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * ADR-002/#28: exercise both real AlertDialog actions without deleting personal history.
 *
 * <p>Espresso targets the dialog window directly; earlier raw accessibility/keycode attempts
 * were unreliable. This test only runs on a disposable API 30 emulator, uses synthetic rows,
 * and restores its fixtures without altering release or production behavior.
 */
@RunWith(AndroidJUnit4.class)
public final class HistoryDeleteConfirmationInstrumentedTest {
    private static final long PRESERVED_WALL = 4102444800000L;
    private static final long TARGET_WALL = 4105036800000L;

    private static void requireDisposableEmulator() {
        assumeTrue("Android 11/API 30 only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Never delete a person's stored history",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    private static Button deleteButtonFor(View root, String description) {
        if (root instanceof Button
                && description.equals(String.valueOf(root.getContentDescription()))) {
            return (Button) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                Button child = deleteButtonFor(group.getChildAt(index), description);
                if (child != null) return child;
            }
        }
        return null;
    }

    private static Button deleteButtonFor(Activity activity, long startedAtMs) {
        String date =
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(startedAtMs));
        return deleteButtonFor(
                activity.getWindow().getDecorView(), "Elimina la sessione del " + date);
    }

    /** Uses the same SQLite v7 sprint schema as the existing delete characterization. */
    private static void addSyntheticSprint(TrackerDb database, long sessionId, long recordedAt) {
        ContentValues sprint = new ContentValues();
        sprint.put("session_id", sessionId);
        sprint.put("recorded_at", recordedAt);
        sprint.put("mode", "TIME");
        sprint.put("target", 30);
        sprint.put("countdown_s", 3);
        sprint.put("elapsed_ms", 10_000L);
        sprint.put("distance_m", 20.0);
        sprint.put("samples", 2);
        sprint.put("outcome", "COMPLETED");
        database.getWritableDatabase().insertOrThrow("sprints", null, sprint);
    }

    @Test
    public void cancelPreservesHistoryThenConfirmDeletesOnlySelectedSessionAndRefreshesUi() {
        requireDisposableEmulator();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = AppPrefs.get(context);
        assertFalse(
                "A disposable emulator must not have an active session",
                prefs.getBoolean("active", false));
        boolean databaseExisted = context.getDatabasePath("sessions.db").exists();

        try (TrackerDb database = new TrackerDb(context)) {
            long preservedId = database.newSession(PRESERVED_WALL);
            long targetId = database.newSession(TARGET_WALL);
            try {
                database.point(
                        preservedId, PRESERVED_WALL + 1_000L,
                        40.0, 14.0, 4.0, 6.0, 2.0, "RUN");
                database.point(
                        targetId, TARGET_WALL + 1_000L,
                        40.0, 14.0, 4.0, 6.0, 2.0, "RUN");
                addSyntheticSprint(database, preservedId, PRESERVED_WALL + 2_000L);
                addSyntheticSprint(database, targetId, TARGET_WALL + 2_000L);
                assertNotNull(database.session(preservedId));
                assertNotNull(database.session(targetId));
                assertEquals("Fixture must have a sprint for the preserved session",
                        1, database.sprints(preservedId).size());
                assertEquals("Fixture must have a sprint for the target session",
                        1, database.sprints(targetId).size());

                try (ActivityScenario<HistoryActivity> scenario =
                        ActivityScenario.launch(HistoryActivity.class)) {
                    scenario.onActivity(
                            activity -> {
                                Button button = deleteButtonFor(activity, TARGET_WALL);
                                assertNotNull("Target delete button must exist", button);
                                assertTrue("Target must be deletable", button.isEnabled());
                                assertTrue("Click must open the confirmation dialog",
                                        button.performClick());
                            });

                    // Unlike a back key or a root accessibility action, Espresso clicks
                    // the actual negative button inside the AlertDialog's own window.
                    onView(withText("Annulla")).inRoot(isDialog()).perform(click());
                    assertNotNull("Cancel must preserve the target session",
                            database.session(targetId));
                    assertEquals("Cancel must preserve the target GPS point",
                            1, database.pointsAfter(targetId, 0, 10).size());
                    assertEquals("Cancel must preserve the target sprint",
                            1, database.sprints(targetId).size());
                    scenario.onActivity(
                            activity -> {
                                Button button = deleteButtonFor(activity, TARGET_WALL);
                                assertNotNull("Cancel must leave the row visible", button);
                                assertTrue(button.isEnabled());
                                assertNotNull("Other session stays visible",
                                        deleteButtonFor(activity, PRESERVED_WALL));
                                assertTrue(button.performClick());
                            });

                    onView(withText("Elimina")).inRoot(isDialog()).perform(click());

                    // The app performs the deletion on a worker thread. Wait for the
                    // real SQLite result and the separately posted main-thread refresh.
                    long deadline = SystemClock.uptimeMillis() + 10_000L;
                    while (database.session(targetId) != null
                            && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(50L);
                    }
                    assertNull("Confirmation must delete the selected session",
                            database.session(targetId));
                    assertEquals("Confirmation must delete its GPS points",
                            0, database.pointsAfter(targetId, 0, 10).size());
                    assertEquals("Confirmation must delete only its sprint rows",
                            0, database.sprints(targetId).size());

                    AtomicBoolean refreshed = new AtomicBoolean(false);
                    while (!refreshed.get() && SystemClock.uptimeMillis() < deadline) {
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                        scenario.onActivity(
                                activity ->
                                        refreshed.set(
                                                deleteButtonFor(activity, TARGET_WALL) == null
                                                        && deleteButtonFor(activity, PRESERVED_WALL)
                                                                != null));
                        if (!refreshed.get()) SystemClock.sleep(50L);
                    }
                    assertTrue("HistoryActivity must remove the deleted row after refresh",
                            refreshed.get());
                }

                // Reopening the Activity must not resurrect the removed row.
                try (ActivityScenario<HistoryActivity> reopened =
                        ActivityScenario.launch(HistoryActivity.class)) {
                    reopened.onActivity(
                            activity -> {
                                assertNull("Deleted row must stay absent after reopening",
                                        deleteButtonFor(activity, TARGET_WALL));
                                assertNotNull("Preserved row must reappear after reopening",
                                        deleteButtonFor(activity, PRESERVED_WALL));
                            });
                }
                assertNotNull("Unselected session must remain in SQLite",
                        database.session(preservedId));
                assertEquals("Unselected GPS point must remain",
                        1, database.pointsAfter(preservedId, 0, 10).size());
                assertEquals("Unselected sprint must remain",
                        1, database.sprints(preservedId).size());
            } finally {
                // Only synthetic IDs allocated by this test may be removed.
                if (database.session(targetId) != null) {
                    assertTrue(database.deleteSession(targetId));
                }
                if (database.session(preservedId) != null) {
                    assertTrue(database.deleteSession(preservedId));
                }
            }
        }
        if (!databaseExisted) {
            assertTrue("Remove only a database created on the disposable emulator",
                    context.deleteDatabase("sessions.db"));
        }
    }
}
