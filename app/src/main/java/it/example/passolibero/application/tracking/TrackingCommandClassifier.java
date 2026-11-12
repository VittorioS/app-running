package it.example.passolibero.application.tracking;

/**
 * Pure classification of existing TrackingService action strings.
 *
 * <p>START, null, and unknown actions intentionally share the legacy foreground-start path.
 * This class does not schedule work, check permissions or acquire the foreground service.
 */
public final class TrackingCommandClassifier {
    private TrackingCommandClassifier() {}

    public enum Command {
        STOP,
        LABEL_WALK,
        LABEL_RUN,
        SPRINT_PREPARE_VOICE,
        SPRINT_START,
        SPRINT_CANCEL,
        START_OR_RESTART
    }

    public static Command classify(String action) {
        if ("it.example.passolibero.STOP".equals(action)) return Command.STOP;
        if ("it.example.passolibero.LABEL_WALK".equals(action)) return Command.LABEL_WALK;
        if ("it.example.passolibero.LABEL_RUN".equals(action)) return Command.LABEL_RUN;
        if ("it.example.passolibero.SPRINT_PREPARE_VOICE".equals(action))
            return Command.SPRINT_PREPARE_VOICE;
        if ("it.example.passolibero.SPRINT_START".equals(action)) return Command.SPRINT_START;
        if ("it.example.passolibero.SPRINT_CANCEL".equals(action)) return Command.SPRINT_CANCEL;
        return Command.START_OR_RESTART;
    }
}
