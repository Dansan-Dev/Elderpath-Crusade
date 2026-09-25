package io.github.elderpath_crusade.multiplayer.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Wraps a connected Socket: a background daemon thread reads newline-delimited messages
 * into a thread-safe queue, drained via pollLine() from the main/render thread — ECS and
 * TypedEventBus aren't thread-safe, so nothing from the network is ever applied directly
 * off the reader thread.
 */
public class NetworkConnection implements Closeable {
    private final Socket socket;
    private final BufferedWriter writer;
    private final BlockingQueue<String> incoming = new LinkedBlockingQueue<>();
    private volatile boolean closed = false;

    public NetworkConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        Thread readerThread = new Thread(this::readLoop, "network-connection-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closed && (line = reader.readLine()) != null) {
                incoming.add(line);
            }
        } catch (IOException ignored) {
            // Socket closed/reset — pollLine() simply stops receiving new lines.
        }
    }

    public synchronized void send(String line) {
        if (closed) return;
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            close();
        }
    }

    /** Non-blocking; returns null if nothing has arrived. Call every frame from the main thread. */
    public String pollLine() {
        return incoming.poll();
    }

    public boolean isConnected() {
        return !closed && !socket.isClosed();
    }

    @Override
    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
