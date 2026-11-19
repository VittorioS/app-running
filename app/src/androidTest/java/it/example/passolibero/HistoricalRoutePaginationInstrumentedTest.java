package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class HistoricalRoutePaginationInstrumentedTest {
    @Test
    public void emptyRouteCompletesAndClosesItsReader() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Field mapField = SessionDetailActivity.class.getDeclaredField("map");
        mapField.setAccessible(true);
        Field workerField = SessionDetailActivity.class.getDeclaredField("routeWorker");
        workerField.setAccessible(true);
        try (TrackerDb db = new TrackerDb(context)) {
            long sessionId = db.newSession(1_000_000L);
            try {
                Intent intent = new Intent(context, SessionDetailActivity.class)
                        .putExtra("session_id", sessionId);
                try (ActivityScenario<SessionDetailActivity> scenario = ActivityScenario.launch(intent)) {
                    long deadline = SystemClock.elapsedRealtime() + 10_000L;
                    boolean[] completed = {false};
                    do {
                        scenario.onActivity(activity -> {
                            TrackMap map = mapOf(mapField, activity);
                            map.view().setUseDataConnection(false);
                            assertTrue(map.sections().isEmpty());
                            try {
                                completed[0] = ((ExecutorService) workerField.get(activity)).isShutdown();
                            } catch (IllegalAccessException error) {
                                throw new AssertionError(error);
                            }
                        });
                        if (!completed[0]) SystemClock.sleep(25L);
                    } while (!completed[0] && SystemClock.elapsedRealtime() < deadline);
                    assertTrue("An empty route must finish after the first read", completed[0]);
                }
            } finally {
                assertTrue(db.deleteSession(sessionId));
            }
        }
    }

    @Test
    public void reopensAndRendersAllPointsBeyondTheOldPageLimit() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        long startedAt = 1_000_000L;
        int expectedPoints = 50_001;
        Field mapField = SessionDetailActivity.class.getDeclaredField("map");
        mapField.setAccessible(true);

        try (TrackerDb db = new TrackerDb(context)) {
            long sessionId = db.newSession(startedAt);
            try {
                db.getWritableDatabase().execSQL(
                        "WITH RECURSIVE ids(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM ids WHERE n < ?) "
                        + "INSERT INTO points (session_id,t_wall,lat,lon,accuracy_m,speed_kmh,delta_m,movement) "
                        + "SELECT ?,? + n * 1000,0,0,5,5,1,'WALK' FROM ids",
                        new Object[] {expectedPoints, sessionId, startedAt});
                RunState session = new RunState();
                session.id = sessionId;
                db.snapshot(session, expectedPoints * 1_000L, true);

                Intent intent = new Intent(context, SessionDetailActivity.class)
                        .putExtra("session_id", sessionId);
                try (ActivityScenario<SessionDetailActivity> scenario = ActivityScenario.launch(intent)) {
                    scenario.onActivity(activity -> mapOf(mapField, activity)
                            .view().setUseDataConnection(false));
                    scenario.recreate();
                    scenario.onActivity(activity -> mapOf(mapField, activity)
                            .view().setUseDataConnection(false));

                    AtomicInteger samples = new AtomicInteger();
                    AtomicInteger sections = new AtomicInteger();
                    long deadline = SystemClock.elapsedRealtime() + 45_000L;
                    while (samples.get() < expectedPoints && SystemClock.elapsedRealtime() < deadline) {
                        scenario.onActivity(activity -> {
                            List<RouteSections.Section> route = mapOf(mapField, activity).sections();
                            sections.set(route.size());
                            samples.set(route.isEmpty() ? 0 : route.get(0).samples);
                        });
                        if (samples.get() < expectedPoints) SystemClock.sleep(25L);
                    }
                    assertEquals("Pages must preserve route continuity", 1, sections.get());
                    assertEquals("The final point must reach the map", expectedPoints, samples.get());
                }
            } finally {
                assertTrue(db.deleteSession(sessionId));
            }
        }
    }

    private static TrackMap mapOf(Field field, SessionDetailActivity activity) {
        try {
            return (TrackMap) field.get(activity);
        } catch (IllegalAccessException error) {
            throw new AssertionError(error);
        }
    }
}
