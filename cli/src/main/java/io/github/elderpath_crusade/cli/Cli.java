package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.ClientMatchSession;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Entry point. Starts the session this run's flags describe (CLI-001), waits for it to reach
 * {@code Phase.PLAYING}, then hands off to {@link CliLoop} — reading commands from stdin
 * (interactive) or the {@code --script} file (CLI-004) — parsed and rendered by CLI-002/CLI-003.
 * No window, no GL, no {@code GameContext}.
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
            try (LineSource lineSource = lineSource(options)) {
                CliLoop.run(session, lineSource, System.out);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("interrupted");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("error: couldn't read " + options.scriptPath() + ": " + e.getMessage());
            System.exit(2);
        }
    }

    private static LineSource lineSource(CliOptions options) throws IOException {
        String scriptPath = options.scriptPath();
        return scriptPath != null ? LineSource.script(Path.of(scriptPath)) : LineSource.interactive(System.in);
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
