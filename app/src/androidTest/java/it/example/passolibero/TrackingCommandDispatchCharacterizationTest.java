package it.example.passolibero;

import android.app.Service;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import it.example.passolibero.application.tracking.TrackingCommandClassifier;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * P4a/#24 G1: a reference trace of the original onStartCommand action ordering.
 *
 * <p>This copies the old dispatch predicates, without starting the real Service, foreground,
 * sensors or worker. Actual Android lifecycle is a separate device/instrumentation gate.
 */
@RunWith(AndroidJUnit4.class)
public final class TrackingCommandDispatchCharacterizationTest {
    enum Command {
        STOP, LABEL_WALK, LABEL_RUN, SPRINT_PREPARE_VOICE, SPRINT_START, SPRINT_CANCEL,
        START_OR_RESTART
    }

    private static Command legacyCommand(String action) {
        if (TrackingService.STOP.equals(action)) return Command.STOP;
        if (TrackingService.LABEL_WALK.equals(action)
                || TrackingService.LABEL_RUN.equals(action)) {
            return TrackingService.LABEL_RUN.equals(action)
                    ? Command.LABEL_RUN : Command.LABEL_WALK;
        }
        if (TrackingService.SPRINT_PREPARE_VOICE.equals(action)) {
            return Command.SPRINT_PREPARE_VOICE;
        }
        if (TrackingService.SPRINT_START.equals(action)) return Command.SPRINT_START;
        if (TrackingService.SPRINT_CANCEL.equals(action)) return Command.SPRINT_CANCEL;
        return Command.START_OR_RESTART;
    }

    private static final class Trace {
        final List<String> effects = new ArrayList<>();
        int returnCode;

        void add(String event) {
            effects.add(event);
        }
    }

    // Reference trace for the immediate action selection. Runnable bodies and FGS calls are
    // represented by markers, not executed; the original Service owns their actual effects.
    private static Trace legacyDispatch(
            Intent intent, BooleanSupplier permissionAndGps, BooleanSupplier foregroundAcquire) {
        String action = intent == null ? null : intent.getAction();
        Command command = legacyCommand(action);
        Trace trace = new Trace();
        if (command == Command.STOP) {
            trace.add("post:finish");
            trace.returnCode = Service.START_NOT_STICKY;
            return trace;
        }
        if (command == Command.LABEL_WALK || command == Command.LABEL_RUN) {
            trace.add(command == Command.LABEL_RUN ? "post:run-label" : "post:walk-label");
            trace.returnCode = Service.START_STICKY;
            return trace;
        }
        if (command == Command.SPRINT_PREPARE_VOICE
                || command == Command.SPRINT_START
                || command == Command.SPRINT_CANCEL) {
            trace.add("post:" + command.name());
            trace.returnCode = Service.START_STICKY;
            return trace;
        }
        trace.add("check:location-and-gps");
        if (!permissionAndGps.getAsBoolean()) {
            trace.add("prefs:error");
            trace.add("stopSelf");
            trace.returnCode = Service.START_NOT_STICKY;
            return trace;
        }
        trace.add("startForeground");
        if (!foregroundAcquire.getAsBoolean()) {
            trace.add("prefs:error");
            trace.add("stopSelf");
            trace.returnCode = Service.START_NOT_STICKY;
            return trace;
        }
        trace.add("post:initialize");
        trace.returnCode = Service.START_STICKY;
        return trace;
    }

    private static Intent action(String name) {
        return new Intent(name);
    }

    @Test public void actionsRemainExactDistinctPublicStrings() {
        String[] actions = {
                TrackingService.START, TrackingService.STOP,
                TrackingService.LABEL_WALK, TrackingService.LABEL_RUN,
                TrackingService.SPRINT_PREPARE_VOICE,
                TrackingService.SPRINT_START, TrackingService.SPRINT_CANCEL
        };
        assertEquals(7, Arrays.stream(actions).distinct().count());
        for (String value : actions) {
            assertTrue(value.startsWith("it.example.passolibero."));
            assertEquals(value, new Intent(value).getAction());
        }
    }

    @Test public void stopIsNotStickyAndNeverChecksForeground() {
        AtomicInteger checks = new AtomicInteger();
        BooleanSupplier forbidden = () -> {
            checks.incrementAndGet();
            throw new AssertionError("STOP must bypass permission and foreground gates");
        };
        Trace trace = legacyDispatch(action(TrackingService.STOP), forbidden, forbidden);
        assertEquals(Service.START_NOT_STICKY, trace.returnCode);
        assertEquals(Arrays.asList("post:finish"), trace.effects);
        assertEquals(0, checks.get());
    }

    @Test public void labelsAndSprintCommandsPostBeforeCheckingAnyPermission() {
        String[] actions = {
                TrackingService.LABEL_WALK, TrackingService.LABEL_RUN,
                TrackingService.SPRINT_PREPARE_VOICE, TrackingService.SPRINT_START,
                TrackingService.SPRINT_CANCEL
        };
        String[] expected = {
                "post:walk-label", "post:run-label", "post:SPRINT_PREPARE_VOICE",
                "post:SPRINT_START", "post:SPRINT_CANCEL"
        };
        AtomicInteger checks = new AtomicInteger();
        BooleanSupplier denied = () -> {
            checks.incrementAndGet();
            return false;
        };
        for (int i = 0; i < actions.length; i++) {
            Trace trace = legacyDispatch(action(actions[i]), denied, denied);
            assertEquals(Service.START_STICKY, trace.returnCode);
            assertEquals(Arrays.asList(expected[i]), trace.effects);
        }
        assertEquals(0, checks.get());
    }

    @Test public void nullUnknownAndExplicitStartTakeForegroundPath() {
        Intent[] intents = {
                null, new Intent(), action(TrackingService.START),
                action("it.example.passolibero.start"),
                action("it.example.passolibero.NOT_A_COMMAND")
        };
        for (Intent intent : intents) {
            Trace accepted = legacyDispatch(intent, () -> true, () -> true);
            assertEquals(Service.START_STICKY, accepted.returnCode);
            assertEquals(Arrays.asList(
                    "check:location-and-gps", "startForeground", "post:initialize"),
                    accepted.effects);
        }
    }

    @Test public void deniedGpsNeverAcquiresForegroundOrQueuesInitialization() {
        AtomicInteger acquired = new AtomicInteger();
        Trace denied = legacyDispatch(action(TrackingService.START), () -> false, () -> {
            acquired.incrementAndGet();
            return true;
        });
        assertEquals(Service.START_NOT_STICKY, denied.returnCode);
        assertEquals(Arrays.asList(
                "check:location-and-gps", "prefs:error", "stopSelf"), denied.effects);
        assertEquals(0, acquired.get());
    }

    @Test public void foregroundAcquisitionFailureStopsWithoutPostingInit() {
        Trace failure = legacyDispatch(action(TrackingService.START), () -> true, () -> false);
        assertEquals(Service.START_NOT_STICKY, failure.returnCode);
        assertEquals(Arrays.asList(
                "check:location-and-gps", "startForeground", "prefs:error", "stopSelf"),
                failure.effects);
        assertFalse(failure.effects.contains("post:initialize"));
    }

    @Test public void nullIntentIsRestartCandidateNotAnImplicitStop() {
        assertEquals(Command.START_OR_RESTART, legacyCommand(null));
        assertEquals(Command.START_OR_RESTART, legacyCommand("START"));
        assertEquals(Command.START_OR_RESTART, legacyCommand(""));
        Trace trace = legacyDispatch(null, () -> true, () -> true);
        assertEquals(Service.START_STICKY, trace.returnCode);
        assertTrue(trace.effects.indexOf("startForeground")
                < trace.effects.indexOf("post:initialize"));
    }
    @Test public void extractedClassifierMatchesLegacyDispatchForEveryOriginalAction() {
        String[] actions = {
                null, "", TrackingService.START, TrackingService.STOP,
                TrackingService.LABEL_WALK, TrackingService.LABEL_RUN,
                TrackingService.SPRINT_PREPARE_VOICE, TrackingService.SPRINT_START,
                TrackingService.SPRINT_CANCEL, "it.example.passolibero.OTHER",
                "it.example.passolibero.stop", "it.example.passolibero.STOP "
        };
        for (String action : actions) {
            assertEquals(
                    "Command branch differs for " + action,
                    legacyCommand(action).name(),
                    TrackingCommandClassifier.classify(action).name());
        }
    }


}
