package it.example.passolibero.application.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** ADR-002/#28 P6a: preserve the history screen's read limit, ordering and error contract. */
public final class ListHistorySessionsTest {
    private static final class FakeReader implements SessionReadPort {
        int requestedLimit = -1;
        int reads;
        List<SessionRow> rows = Collections.emptyList();
        RuntimeException failure;

        @Override
        public List<SessionRow> recent(int count) {
            reads++;
            requestedLimit = count;
            if (failure != null) throw failure;
            return rows;
        }

        @Override
        public SessionRow session(long id) {
            throw new AssertionError("History listing must not query individual sessions");
        }

        @Override
        public List<PointRow> pointsAfter(long sessionId, long afterId, int limit) {
            throw new AssertionError("History listing must not load GPS points");
        }

        @Override
        public List<SprintRow> sprints(long sessionId) {
            throw new AssertionError("History listing must not load sprints");
        }

        @Override
        public boolean hasTaggedSprintPoints(long sessionId, long from, long until) {
            throw new AssertionError("History listing must not check sprint points");
        }
    }

    private static SessionReadPort.SessionRow row(long id, long startedAt) {
        return new SessionReadPort.SessionRow(
                id,
                startedAt,
                120_000L,
                120.5,
                42,
                50.5,
                70,
                "COMPLETED",
                60_000L,
                20_000L,
                40_000L,
                40_000L,
                1_000,
                20_000L,
                0L,
                null,
                null,
                130_000L,
                null,
                null);
    }

    @Test
    public void requestsExactlyLegacyLimitAndPreservesOrderAndRowValues() {
        FakeReader reader = new FakeReader();
        SessionReadPort.SessionRow recent = row(12, 300);
        SessionReadPort.SessionRow older = row(8, 100);
        reader.rows = Arrays.asList(recent, older);

        List<SessionReadPort.SessionRow> actual = new ListHistorySessions(reader).execute();

        assertSame(reader.rows, actual);
        assertSame(recent, actual.get(0));
        assertSame(older, actual.get(1));
        assertEquals(200, reader.requestedLimit);
        assertEquals(1, reader.reads);
        assertEquals(42, actual.get(0).steps);
        assertEquals("COMPLETED", actual.get(1).status);
        assertEquals(null, actual.get(0).checkpointIntervalM);
    }

    @Test
    public void emptyHistoryIsPassedThroughWithoutExtraQueries() {
        FakeReader reader = new FakeReader();
        assertSame(reader.rows, new ListHistorySessions(reader).execute());
        assertEquals(1, reader.reads);
        assertEquals(200, reader.requestedLimit);
    }

    @Test
    public void sqliteFailureIsNotHiddenByUseCase() {
        FakeReader reader = new FakeReader();
        RuntimeException failure = new IllegalStateException("read failed");
        reader.failure = failure;
        try {
            new ListHistorySessions(reader).execute();
            fail("The screen must preserve the existing error message path");
        } catch (RuntimeException caught) {
            assertSame(failure, caught);
        }
        assertEquals(1, reader.reads);
    }
}
