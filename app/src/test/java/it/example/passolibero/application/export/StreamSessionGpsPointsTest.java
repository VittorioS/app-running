package it.example.passolibero.application.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** ADR-002/#30 G1: route streaming preserves order and propagates write errors. */
public final class StreamSessionGpsPointsTest {
    private static final class FakePointPort implements SessionGpsExportPort {
        int calls;
        long requestedSessionId = -1;
        List<Point> points = new ArrayList<>();
        IOException sourceFailure;

        @Override
        public void forEachPoint(long sessionId, Sink sink) throws IOException {
            calls++;
            requestedSessionId = sessionId;
            if (sourceFailure != null) throw sourceFailure;
            for (Point point : points) {
                sink.accept(point);
            }
        }
    }

    @Test
    public void streamsEveryPointInSourceOrderWithoutExtraReads() throws IOException {
        FakePointPort source = new FakePointPort();
        SessionGpsExportPort.Point first =
                new SessionGpsExportPort.Point(9_000, 40.2, 14.1, 5.2, 7.1, 2.4, "RUN");
        SessionGpsExportPort.Point second =
                new SessionGpsExportPort.Point(1_000, 40.4, 14.3, 3.5, 4.2, 1.1, "MOVING_UNKNOWN");
        source.points = Arrays.asList(first, second);
        List<SessionGpsExportPort.Point> received = new ArrayList<>();

        new StreamSessionGpsPoints(source).execute(42, received::add);

        assertEquals(1, source.calls);
        assertEquals(42, source.requestedSessionId);
        assertEquals(2, received.size());
        assertSame(first, received.get(0));
        assertSame(second, received.get(1));
        assertEquals("RUN", received.get(0).movement);
        assertEquals("MOVING_UNKNOWN", received.get(1).movement);
    }

    @Test
    public void anEmptyRouteDoesNotCreateArtificialPoints() throws IOException {
        FakePointPort source = new FakePointPort();
        List<SessionGpsExportPort.Point> result = new ArrayList<>();
        new StreamSessionGpsPoints(source).execute(8, result::add);
        assertEquals(1, source.calls);
        assertEquals(8, source.requestedSessionId);
        assertEquals(0, result.size());
    }

    @Test
    public void propagatesOutputIoErrorWithoutReadingLaterPoints() {
        FakePointPort source = new FakePointPort();
        source.points =
                Arrays.asList(
                        new SessionGpsExportPort.Point(1, 1, 2, 3, 4, 5, "RUN"),
                        new SessionGpsExportPort.Point(2, 6, 7, 8, 9, 10, "WALK"));
        IOException outputFailure = new IOException("destination full");
        int[] visited = {0};

        SessionGpsExportPort.Sink failingSink =
                ignored -> {
                    visited[0]++;
                    throw outputFailure;
                };
        try {
            new StreamSessionGpsPoints(source).execute(3, failingSink);
            fail("The export cannot silently succeed after an IO error");
        } catch (IOException caught) {
            assertSame(outputFailure, caught);
        }
        assertEquals(1, visited[0]);
        assertEquals(1, source.calls);
    }

    @Test
    public void propagatesSourceFailureUnchanged() {
        FakePointPort source = new FakePointPort();
        IOException error = new IOException("database read interrupted");
        source.sourceFailure = error;
        try {
            new StreamSessionGpsPoints(source).execute(5, ignored -> {});
            fail("The export must report source errors to the caller");
        } catch (IOException caught) {
            assertSame(error, caught);
        }
        assertEquals(1, source.calls);
    }
}
