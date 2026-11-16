package io.github.elderpath_crusade.server.launcher;

import io.github.elderpath_crusade.lobby.MatchLauncher;
import io.github.elderpath_crusade.multiplayer.net.NetworkConnection;
import io.github.elderpath_crusade.net.transport.LineLink;
import io.github.elderpath_crusade.server.ServerProcess;
import io.github.elderpath_crusade.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs each lobby match in its own child ServerLauncher process (both seats human, localhost
 * only, ephemeral port), like a client's solo/hotseat match — the one-match-per-process rule
 * that keeps the GameContext singleton valid is untouched. Starting a JVM takes seconds, so
 * launch() works on a background thread; stopping one (which may wait out a grace period) does too.
 */
final class ProcessMatchLauncher implements MatchLauncher, AutoCloseable {
    /** Longer than the lobby's 20-minute rejoin hold: while a dropped player may still come back the match must outlive an empty room. */
    private static final int CHILD_IDLE_TIMEOUT_SECONDS = 21 * 60;

    private final File assetsDir;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "match-launcher");
        thread.setDaemon(true);
        return thread;
    });

    /** assetsDir null: the child reads its data from the classpath, like the parent. */
    ProcessMatchLauncher(File assetsDir) {
        this.assetsDir = assetsDir;
    }

    @Override
    public CompletableFuture<MatchEndpoint> launch(MatchSpec spec) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ServerProcess process = ServerProcess.start(ServerProcess.Options.of(arguments(spec).toArray(String[]::new)));
                Logger.log("ProcessMatchLauncher", "match server up on port " + process.getPort());
                return (MatchEndpoint) new ProcessEndpoint(process);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /** The ServerLauncher flags for a lobby match. */
    List<String> arguments(MatchSpec spec) throws IOException {
        List<String> args = new ArrayList<>(List.of(
                "--bind", "127.0.0.1", "--port", "0",
                "--p1", "human", "--p2", "human",
                "--seed", Long.toString(spec.seed()),
                "--p1-deck", deckArgument(spec.p1Deck()),
                "--p2-deck", deckArgument(spec.p2Deck()),
                "--idle-timeout-s", Integer.toString(CHILD_IDLE_TIMEOUT_SECONDS)));
        if (assetsDir != null) {
            args.add("--assets");
            args.add(assetsDir.getAbsolutePath());
        }
        return args;
    }

    /** ["Wolf", "Wolf", "Crow"] → "Wolf:2,Crow:1". A name the flag syntax can't carry is refused, never escaped. */
    static String deckArgument(List<String> deck) throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String card : deck) {
            if (card == null || card.isBlank() || card.contains(",") || card.contains(":")) {
                throw new IOException("card name can't be passed to a match server: " + card);
            }
            counts.merge(card, 1, Integer::sum);
        }
        StringBuilder out = new StringBuilder();
        counts.forEach((card, count) -> {
            if (out.length() > 0) out.append(',');
            out.append(card).append(':').append(count);
        });
        return out.toString();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private final class ProcessEndpoint implements MatchEndpoint {
        private final ServerProcess process;
        private boolean closed;

        ProcessEndpoint(ServerProcess process) {
            this.process = process;
        }

        @Override
        public LineLink connect() throws IOException {
            Socket socket = new Socket("127.0.0.1", process.getPort());
            socket.setTcpNoDelay(true);
            return new NetworkConnection(socket);
        }

        @Override
        public boolean isAlive() {
            return process.isAlive();
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            executor.execute(process::stop);
        }
    }
}
