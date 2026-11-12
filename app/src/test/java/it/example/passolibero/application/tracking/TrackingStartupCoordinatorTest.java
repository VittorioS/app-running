package it.example.passolibero.application.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * ADR-002/#24 G2: deterministic characterization of the legacy foreground-start ordering.
 *
 * <p>These tests use fake Android effects. They supplement, but cannot replace, the instrumented
 * TrackingService and physical-device lifecycle checks.
 */
public final class TrackingStartupCoordinatorTest {
    @Test
    public void deniedLocationShortCircuitsGpsAndNeverAcquiresForeground() {
        List<String> calls = new ArrayList<>();

        TrackingStartupCoordinator.StartResult result =
                TrackingStartupCoordinator.start(
                        () -> {
                            calls.add("fine");
                            return false;
                        },
                        () -> {
                            fail("GPS cannot be queried without permission");
                            return true;
                        },
                        () -> calls.add("denied"),
                        () -> fail("foreground must not be acquired"),
                        ignored -> fail("foreground error handler must not run"),
                        () -> fail("worker must not be scheduled"));

        assertEquals(TrackingStartupCoordinator.StartResult.NOT_STARTED, result);
        assertEquals(Arrays.asList("fine", "denied"), calls);
    }

    @Test
    public void gpsUnavailableNotifiesAndStopsBeforeForeground() {
        List<String> calls = new ArrayList<>();

        TrackingStartupCoordinator.StartResult result =
                TrackingStartupCoordinator.start(
                        () -> {
                            calls.add("fine");
                            return true;
                        },
                        () -> {
                            calls.add("gps");
                            return false;
                        },
                        () -> calls.add("denied"),
                        () -> fail("foreground must not be acquired"),
                        ignored -> fail("foreground error handler must not run"),
                        () -> fail("worker must not be scheduled"));

        assertEquals(TrackingStartupCoordinator.StartResult.NOT_STARTED, result);
        assertEquals(Arrays.asList("fine", "gps", "denied"), calls);
    }

    @Test
    public void successfulStartAcquiresForegroundSynchronouslyBeforePostingWork() {
        List<String> calls = new ArrayList<>();

        TrackingStartupCoordinator.StartResult result =
                TrackingStartupCoordinator.start(
                        () -> {
                            calls.add("fine");
                            return true;
                        },
                        () -> {
                            calls.add("gps");
                            return true;
                        },
                        () -> fail("denial callback must not run"),
                        () -> calls.add("foreground"),
                        ignored -> fail("failure callback must not run"),
                        () -> calls.add("enqueue"));

        assertEquals(TrackingStartupCoordinator.StartResult.STARTED, result);
        assertEquals(Arrays.asList("fine", "gps", "foreground", "enqueue"), calls);
    }

    @Test
    public void foregroundRuntimeFailureRunsOnlyErrorHandlerAndDoesNotPostWorker() {
        List<String> calls = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("FGS unavailable");
        RuntimeException[] captured = new RuntimeException[1];

        TrackingStartupCoordinator.StartResult result =
                TrackingStartupCoordinator.start(
                        () -> {
                            calls.add("fine");
                            return true;
                        },
                        () -> {
                            calls.add("gps");
                            return true;
                        },
                        () -> fail("GPS denial must not run"),
                        () -> {
                            calls.add("foreground");
                            throw failure;
                        },
                        error -> {
                            calls.add("failed");
                            captured[0] = error;
                        },
                        () -> fail("worker cannot be posted after failed foreground"));

        assertEquals(TrackingStartupCoordinator.StartResult.NOT_STARTED, result);
        assertEquals(Arrays.asList("fine", "gps", "foreground", "failed"), calls);
        assertSame(failure, captured[0]);
    }

    @Test
    public void permissionGetterFailurePropagatesBeforeAnyEffect() {
        RuntimeException failure = new IllegalArgumentException("permission read failed");

        try {
            TrackingStartupCoordinator.start(
                    () -> {
                        throw failure;
                    },
                    () -> {
                        fail("GPS must not be read");
                        return true;
                    },
                    () -> fail("denial must not run"),
                    () -> fail("foreground must not run"),
                    ignored -> fail("foreground failure callback must not run"),
                    () -> fail("worker must not run"));
            fail("Permission getter failure must propagate unchanged");
        } catch (RuntimeException expected) {
            assertSame(failure, expected);
        }
    }

    @Test
    public void enqueueFailurePropagatesOutsideForegroundExceptionHandler() {
        RuntimeException failure = new IllegalStateException("worker queue unavailable");
        List<String> calls = new ArrayList<>();

        try {
            TrackingStartupCoordinator.start(
                    () -> true,
                    () -> true,
                    () -> fail("denied callback must not run"),
                    () -> calls.add("foreground"),
                    ignored -> fail("foreground error handler must not catch worker failures"),
                    () -> {
                        calls.add("enqueue");
                        throw failure;
                    });
            fail("Worker queue failure must propagate");
        } catch (RuntimeException expected) {
            assertSame(failure, expected);
        }
        assertEquals(Arrays.asList("foreground", "enqueue"), calls);
    }

    @Test
    public void foregroundFailureHandlerExceptionsAreNotSwallowed() {
        RuntimeException callbackFailure = new IllegalStateException("stopping failed");
        try {
            TrackingStartupCoordinator.start(
                    () -> true,
                    () -> true,
                    () -> fail("denial must not run"),
                    () -> {
                        throw new IllegalArgumentException("foreground failed");
                    },
                    ignored -> {
                        throw callbackFailure;
                    },
                    () -> fail("worker must not run"));
            fail("Error callback exceptions must propagate");
        } catch (RuntimeException expected) {
            assertSame(callbackFailure, expected);
        }
    }
}
