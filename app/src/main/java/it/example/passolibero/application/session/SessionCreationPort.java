package it.example.passolibero.application.session;

/**
 * One-operation boundary for allocating a persisted session ID.
 *
 * <p>Implementations preserve SQLite's insert-or-throw behavior and join an existing caller
 * transaction without creating or committing a new one.
 */
public interface SessionCreationPort {
    long createSession(long startedAtWallMs);
}
