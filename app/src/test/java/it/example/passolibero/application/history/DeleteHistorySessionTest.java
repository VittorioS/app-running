package it.example.passolibero.application.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** ADR-002/#28 P6a: characterize the history delete boundary before moving SQLite writes. */
public final class DeleteHistorySessionTest {
    private static final class FakeDeletePort implements SessionDeletePort {
        long lastId = -1;
        int calls;
        boolean result;
        RuntimeException failure;

        @Override
        public boolean deleteSession(long sessionId) {
            calls++;
            lastId = sessionId;
            if (failure != null) throw failure;
            return result;
        }
    }

    @Test
    public void rejectsInvalidSessionIdsWithoutInvokingPersistence() {
        FakeDeletePort port = new FakeDeletePort();
        DeleteHistorySession useCase = new DeleteHistorySession(port);

        assertFalse(useCase.execute(0));
        assertFalse(useCase.execute(-42));
        assertEquals(0, port.calls);
    }

    @Test
    public void passesThroughSuccessAndDoesNotRetry() {
        FakeDeletePort port = new FakeDeletePort();
        port.result = true;

        assertTrue(new DeleteHistorySession(port).execute(71));
        assertEquals(1, port.calls);
        assertEquals(71, port.lastId);
    }

    @Test
    public void preservesBlockedOrMissingRowResult() {
        FakeDeletePort port = new FakeDeletePort();

        assertFalse(new DeleteHistorySession(port).execute(15));
        assertEquals(1, port.calls);
        assertEquals(15, port.lastId);
    }

    @Test
    public void propagatesDatabaseFailureToLegacyUiErrorHandling() {
        FakeDeletePort port = new FakeDeletePort();
        RuntimeException failure = new IllegalStateException("SQLite write failed");
        port.failure = failure;

        try {
            new DeleteHistorySession(port).execute(31);
            fail("Do not turn an exception into successful deletion");
        } catch (RuntimeException caught) {
            assertSame(failure, caught);
        }
        assertEquals(1, port.calls);
    }
}
