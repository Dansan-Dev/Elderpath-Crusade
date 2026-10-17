package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.utils.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A headless match server (the {@code server} module's ServerLauncher) running as a child
 * process — how the game hosts its own match for solo, hotseat and hosted play, and how tests
 * run a real server out of process.
 *
 * The child's stdout is a line protocol: {@code LISTENING <port>} once bound,
 * {@code MATCH_ENDED <winner|NONE>}, {@code ERROR <message>}. Its stderr is forwarded to
 * Logger under the "server" tag. The child exits when its stdin closes or this process dies
 * (it is passed --parent-pid), so a crashed game never leaves a server running.
 *
 * Classpath: Options.classpath, else the {@value #CLASSPATH_PROPERTY} system property, else
 * this JVM's own classpath (works whenever the server module is on it, e.g. in its tests).
 * A packaged client points it at {@code server/build/install/server/lib/*} from
 * {@code ./gradlew :server:installDist}.
 */
public final class ServerProcess implements AutoCloseable {
    public static final String MAIN_CLASS = "io.github.elderpath_crusade.server.launcher.ServerLauncher";
    public static final String CLASSPATH_PROPERTY = "elderpath.serverClasspath";
    public static final String LISTENING = "LISTENING";
    public static final String MATCH_ENDED = "MATCH_ENDED";
    public static final String ERROR = "ERROR";

    /** How to launch the child. args are ServerLauncher flags (see its LaunchOptions). */
    public record Options(List<String> args, String classpath, File workingDir,
                          Duration startTimeout, Duration stopGrace) {
        public Options {
            args = List.copyOf(args);
        }

        public static Options of(String... args) {
            return new Options(List.of(args), null, null, Duration.ofSeconds(20), Duration.ofSeconds(3));
        }

        public Options withClasspath(String value) {
            return new Options(args, value, workingDir, startTimeout, stopGrace);
        }

        public Options withWorkingDir(File value) {
            return new Options(args, classpath, value, startTimeout, stopGrace);
        }

        public Options withStartTimeout(Duration value) {
            return new Options(args, classpath, workingDir, value, stopGrace);
        }
    }

    private final Process process;
    private final Options options;
    private final CountDownLatch listening = new CountDownLatch(1);
    private final CountDownLatch matchEnded = new CountDownLatch(1);
    private volatile int port = -1;
    private volatile String winner;
    private volatile String error;

    private ServerProcess(Process process, Options options) {
        this.process = process;
        this.options = options;
    }

    /** Launches the server and waits until it is listening. Throws if it fails or times out first. */
    public static ServerProcess start(Options options) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.add("-cp");
        command.add(resolveClasspath(options));
        command.add(MAIN_CLASS);
        command.addAll(options.args());
        command.add("--parent-pid");
        command.add(Long.toString(ProcessHandle.current().pid()));

        ProcessBuilder builder = new ProcessBuilder(command);
        if (options.workingDir() != null) builder.directory(options.workingDir());
        ServerProcess server = new ServerProcess(builder.start(), options);
        server.pump(server.process.getInputStream(), "server-stdout", server::onProtocolLine);
        server.pump(server.process.getErrorStream(), "server-stderr", line -> Logger.log("server", line));
        server.awaitListening();
        return server;
    }

    public int getPort() {
        return port;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /** The winner the server reported ("P1", "P2" or "NONE"), or null while the match is still on. */
    public String getWinner() {
        return winner;
    }

    /** Waits for MATCH_ENDED; returns the winner, or null on timeout. */
    public String awaitMatchEnded(Duration timeout) throws InterruptedException {
        matchEnded.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        return winner;
    }

    /** Waits for the process to exit; returns its exit code, or null on timeout. */
    public Integer awaitExit(Duration timeout) throws InterruptedException {
        return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS) ? process.exitValue() : null;
    }

    /** Closes the server's stdin (it exits on its own), then kills it if it outlives the grace period. */
    public void stop() {
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // Already closed — the server is exiting or gone.
        }
        try {
            if (awaitExit(options.stopGrace()) == null) {
                process.destroy();
                if (awaitExit(Duration.ofSeconds(2)) == null) process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void awaitListening() throws IOException {
        boolean signalled;
        try {
            signalled = listening.await(options.startTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            signalled = false;
        }
        if (signalled && port > 0) return;
        process.destroyForcibly();
        throw new IOException(error != null
                ? "match server failed to start: " + error
                : "match server did not report " + LISTENING + " within " + options.startTimeout());
    }

    private void onProtocolLine(String line) {
        int space = line.indexOf(' ');
        String keyword = space < 0 ? line : line.substring(0, space);
        String value = space < 0 ? "" : line.substring(space + 1).trim();
        switch (keyword) {
            case LISTENING -> {
                try {
                    port = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    error = "bad " + LISTENING + " line: " + line;
                }
                listening.countDown();
            }
            case MATCH_ENDED -> {
                winner = value;
                matchEnded.countDown();
            }
            case ERROR -> {
                error = value;
                Logger.error("server", line);
                listening.countDown();
            }
            default -> Logger.log("server", "unexpected stdout line: " + line);
        }
    }

    /** Reads stream line by line on a daemon thread; when it ends, unblocks anyone still waiting. */
    private void pump(InputStream stream, String name, Consumer<String> onLine) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) onLine.accept(line);
            } catch (IOException ignored) {
                // Process gone — nothing more to read.
            } finally {
                listening.countDown();
                matchEnded.countDown();
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static String resolveClasspath(Options options) {
        if (options.classpath() != null) return options.classpath();
        String configured = System.getProperty(CLASSPATH_PROPERTY);
        return configured != null ? configured : System.getProperty("java.class.path");
    }

    private static String javaBinary() {
        return ProcessHandle.current().info().command()
                .orElse(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
    }
}
