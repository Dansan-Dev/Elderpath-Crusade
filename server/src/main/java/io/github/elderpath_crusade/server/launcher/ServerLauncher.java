package io.github.elderpath_crusade.server.launcher;

import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.net.transport.TcpTransport;
import io.github.elderpath_crusade.server.MatchServer;
import io.github.elderpath_crusade.server.ServerProcess;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Headless match server process: one authoritative match, reached over TCP (see MatchServer).
 *
 * Stdout carries only the machine-readable lines a parent process reads (see ServerProcess):
 * {@code LISTENING <port>} once bound, {@code MATCH_ENDED <winner|NONE>} when the match ends,
 * {@code ERROR <message>} on failure. Everything else, including the simulation's own logging,
 * goes to stderr. Exits when the match has ended (after a short grace), when no human client has
 * been connected for --idle-timeout-s, when stdin closes, or when --parent-pid is gone — so a
 * crashed game never leaves an orphaned server behind.
 */
public final class ServerLauncher {
    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;
    private static final String PIECES = "data/pieces.yaml";

    private final PrintStream protocol;
    private volatile String stopReason;

    private ServerLauncher(PrintStream protocol) {
        this.protocol = protocol;
    }

    public static void main(String[] args) {
        // Claim stdout for the protocol before anything can print to it.
        PrintStream protocol = System.out;
        System.setOut(System.err);
        ServerLauncher launcher = new ServerLauncher(protocol);

        LaunchOptions options;
        try {
            options = LaunchOptions.parse(args);
        } catch (IllegalArgumentException e) {
            launcher.report(ServerProcess.ERROR + " " + e.getMessage());
            System.exit(EXIT_USAGE);
            return;
        }
        int code;
        try {
            code = launcher.run(options);
        } catch (Exception e) {
            e.printStackTrace();
            launcher.report(ServerProcess.ERROR + " " + e);
            code = EXIT_ERROR;
        }
        System.exit(code);
    }

    private int run(LaunchOptions options) throws IOException, InterruptedException {
        loadDefinitions(options);
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        MatchConfig config = MatchConfig.standard(GameMode.ONLINE_MATCH,
                options.decks().get(PieceAlignment.P1), options.decks().get(PieceAlignment.P2),
                options.seed(), options.bots());

        TcpTransport transport = new TcpTransport(options.bind(), options.port());
        MatchServer server = new MatchServer(config, transport);
        report(ServerProcess.LISTENING + " " + transport.getPort());
        log("listening on " + options.bind() + ":" + transport.getPort() + ", seats " + seatSummary(options));

        if (options.watchStdin()) startDaemon("stdin-watch", this::watchStdin);
        if (options.parentPid() != null) startDaemon("parent-watch", () -> watchParent(options.parentPid()));

        try {
            loop(server, options);
        } finally {
            server.close();
        }
        log("stopped: " + stopReason);
        return EXIT_OK;
    }

    private void loop(MatchServer server, LaunchOptions options) throws InterruptedException {
        List<PieceAlignment> humans = options.humanSides();
        long tickNanos = options.tickMs() * 1_000_000L;
        long idleNanos = options.idleTimeoutSeconds() * 1_000_000_000L;
        long graceNanos = options.endGraceSeconds() * 1_000_000_000L;
        long last = System.nanoTime();
        long lastConnected = last;
        long endedAt = -1;

        while (stopReason == null) {
            long now = System.nanoTime();
            server.tick((now - last) / 1e9f * options.timeScale());
            last = now;

            boolean anyConnected = humans.stream().anyMatch(server::isConnected);
            if (anyConnected) lastConnected = now;

            if (server.isEnded() && endedAt < 0) {
                endedAt = now;
                PieceAlignment winner = server.getWinner();
                report(ServerProcess.MATCH_ENDED + " " + (winner != null ? winner.name() : "NONE"));
            }
            if (endedAt >= 0 && (!anyConnected || now - endedAt >= graceNanos)) stopReason = "match ended";
            else if (!humans.isEmpty() && !anyConnected && now - lastConnected >= idleNanos) {
                stopReason = "no client connected for " + options.idleTimeoutSeconds() + " s";
            }

            long sleepNanos = tickNanos - (System.nanoTime() - now);
            if (sleepNanos > 0) Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
        }
    }

    /** Points Gdx.files at the assets and loads pieces, abilities, spells and the card catalog. */
    private static void loadDefinitions(LaunchOptions options) {
        Gdx.files = new AssetFiles(resolveAssetsDir(options));
        PieceRegistry.load();
        AbilityRegistry.load();
        CardCatalog.initialize();
        for (List<String> deck : options.decks().values()) {
            for (String card : deck) {
                if (CardCatalog.get(card) == null) throw new IllegalArgumentException("unknown card in deck: " + card);
            }
        }
    }

    /**
     * The assets directory to read from, or null to read from the classpath. An explicit --assets
     * must hold the data; without it, the working directory is used if it does (the dev layout,
     * where the game runs from assets/), else the classpath (a packaged game's jar).
     */
    private static File resolveAssetsDir(LaunchOptions options) {
        if (options.assetsDir() != null) {
            File assets = options.assetsDir().getAbsoluteFile();
            if (!hasData(assets)) throw new IllegalStateException("no " + PIECES + " under assets directory " + assets);
            return assets;
        }
        File workingDir = new File(".").getAbsoluteFile();
        if (hasData(workingDir)) return workingDir;
        if (ServerLauncher.class.getClassLoader().getResource(PIECES) != null) return null;
        throw new IllegalStateException("no " + PIECES + " in the working directory " + workingDir + " or on the classpath");
    }

    private static boolean hasData(File assetsDir) {
        return new File(assetsDir, PIECES).isFile();
    }

    private void watchStdin() {
        try {
            while (System.in.read() != -1) {
                // Input is ignored; only the pipe closing matters.
            }
        } catch (IOException ignored) {
            // Treated the same as EOF.
        }
        stop("stdin closed");
    }

    private void watchParent(long pid) {
        while (stopReason == null) {
            boolean alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
            if (!alive) {
                stop("parent process " + pid + " exited");
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void stop(String reason) {
        if (stopReason == null) stopReason = reason;
    }

    private static void startDaemon(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static String seatSummary(LaunchOptions options) {
        StringBuilder out = new StringBuilder();
        for (PieceAlignment side : new PieceAlignment[]{PieceAlignment.P1, PieceAlignment.P2}) {
            if (out.length() > 0) out.append(", ");
            out.append(side).append('=').append(options.bots().containsKey(side) ? options.bots().get(side) : "HUMAN");
        }
        return out.toString();
    }

    private synchronized void report(String line) {
        protocol.println(line);
        protocol.flush();
    }

    private static void log(String message) {
        System.err.println("[ServerLauncher] " + message);
    }
}
