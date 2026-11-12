package it.example.passolibero.application.history;

/** Port for deleting one persisted session with its GPS points and sprint records. */
public interface SessionDeletePort {
    /**
     * Returns false for a blocked or missing session. Database failures propagate
     * to the legacy screen so its existing error handling can report them.
     */
    boolean deleteSession(long sessionId);
}
