package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.ClientMatchSession;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Entry point. Starts the session this run's flags describe (CLI-001), waits for it to reach
 * {@code Phase.PLAYING}, then hands off to {@link CliLoop} — reading commands from stdin
 * (interactive) or the {@code --script} file (CLI-004) — parsed and rendered by CLI-002/CLI-003.
 * No window, no GL, no {@code GameContext}. With {@code --soak N} (CLI-006) it instead plays N
 * games unattended through {@link SoakRunner} and exits non-zero if any of them failed.
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

        if (options.soakGames() != null) {
            runSoak(options);
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

    /** CLI-006: --soak N — unattended games, exit 1 if any failed. */
    private static void runSoak(CliOptions options) {
        long baseSeed = options.seed() != null ? options.seed() : System.currentTimeMillis();
        SoakRunner runner = new SoakRunner(options.soakStrategy(), options.soakMaxTurns(), System.out);
        try {
            SoakRunner.Summary summary = runner.run(options.soakGames(), baseSeed,
                    seed -> options.startSoakSession(seed, SoakRunner.FAST_SERVER));
            System.exit(summary.ok() ? 0 : 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("interrupted");
            System.exit(1);
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
