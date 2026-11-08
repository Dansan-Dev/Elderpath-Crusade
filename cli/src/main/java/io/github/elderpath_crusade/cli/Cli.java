package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.ClientMatchState;

/**
 * Entry point. CLI-001: proves a session reaches {@code Phase.PLAYING} headlessly — no window,
 * no GL, no {@code GameContext}. The interactive/scripted render-and-dispatch loop is CLI-004's
 * job; this just starts the session this run's flags describe and reports where it landed.
 */
public final class Cli {
    private Cli() {}

    public static void main(String[] args) {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(2);
            return;
        }

        try (ClientMatchSession session = options.startSession()) {
            ClientMatchSession.Status status = awaitReadyOrOver(session);
            if (status.isOver()) {
                System.err.println("session ended before the match started: " + status.phase() + " " + status.detail());
                System.exit(1);
                return;
            }
            System.out.println("PLAYING");
            ClientMatchState state = session.viewState();
            System.out.println(state != null ? state.toView() : "(no state yet)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("interrupted while waiting for the session to start");
            System.exit(1);
        }
    }

    private static ClientMatchSession.Status awaitReadyOrOver(ClientMatchSession session) throws InterruptedException {
        while (true) {
            session.poll();
            ClientMatchSession.Status status = session.getStatus();
            if (status.isReady() || status.isOver()) return status;
            Thread.sleep(20);
        }
    }
}
