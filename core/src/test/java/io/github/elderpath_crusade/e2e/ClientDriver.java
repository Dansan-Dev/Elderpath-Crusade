package io.github.elderpath_crusade.e2e;

/** Plays a TestClient's side through its MatchClient, reading only its ClientMatchState. */
@FunctionalInterface
public interface ClientDriver {
    /**
     * Called by the harness once everything sent so far has been delivered. Sends at most one
     * command and returns whether it did — the harness delivers its result before calling again.
     */
    boolean act(TestClient client);
}
