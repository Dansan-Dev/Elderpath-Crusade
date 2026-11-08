package io.github.elderpath_crusade.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Where {@link CliLoop} reads command lines from — interactive stdin (read on a background
 * thread so the loop can keep polling the session between lines, per the plan's render loop) or
 * a pre-read script file (no waiting, no prompt). Never throws past construction; a read error
 * is treated as end of input.
 */
interface LineSource extends AutoCloseable {
    /** The next command line, waiting up to timeoutMillis for one; null if none arrived (not EOF — keep looping). */
    String nextLine(long timeoutMillis) throws InterruptedException;

    /** True once every line this source will ever produce has been consumed by nextLine. */
    boolean exhausted();

    /** Whether to print a "> " prompt before waiting on a line (interactive only). */
    boolean interactive();

    @Override
    default void close() {}

    static LineSource interactive(InputStream in) {
        return new Interactive(in);
    }

    static LineSource script(List<String> lines) {
        return new Script(lines);
    }

    static LineSource script(Path file) throws IOException {
        return new Script(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    /** Reads stdin on a daemon thread into a queue, so the main loop never blocks past one poll. */
    final class Interactive implements LineSource {
        private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        private final Thread reader;

        private Interactive(InputStream in) {
            reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) queue.put(line);
                } catch (IOException | InterruptedException ignored) {
                    // Stream closed or interrupted — fall through to marking input exhausted.
                } finally {
                    closed = true;
                }
            }, "cli-stdin-reader");
            reader.setDaemon(true);
            reader.start();
        }

        @Override
        public String nextLine(long timeoutMillis) throws InterruptedException {
            return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public boolean exhausted() {
            return closed && queue.isEmpty();
        }

        @Override
        public boolean interactive() {
            return true;
        }
    }

    /** Lines read upfront; no waiting — nextLine returns the next one (or null) immediately. */
    final class Script implements LineSource {
        private final List<String> lines;
        private int index;

        private Script(List<String> lines) {
            this.lines = List.copyOf(lines);
        }

        @Override
        public String nextLine(long timeoutMillis) {
            return index < lines.size() ? lines.get(index++) : null;
        }

        @Override
        public boolean exhausted() {
            return index >= lines.size();
        }

        @Override
        public boolean interactive() {
            return false;
        }
    }
}
