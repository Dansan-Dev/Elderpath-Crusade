package io.github.elderpath_crusade.multiplayer.net;

import io.github.elderpath_crusade.net.transport.LineLink;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Wraps a connected Socket: a background daemon thread reads newline-delimited messages
 * into a thread-safe queue, drained via pollLine() from the main/render thread — ECS and
 * TypedEventBus aren't thread-safe, so nothing from the network is ever applied directly
 * off the reader thread.
 *
 * The peer is untrusted: a line longer than maxLineChars, or more than maxQueuedLines waiting
 * to be polled, closes the connection instead of growing this process's memory.
 */
public class NetworkConnection implements Closeable, LineLink {
    /** Limits for a connection a server accepted: client messages are a few hundred bytes. */
    public static final int SERVER_MAX_LINE_CHARS = 8 * 1024;
    public static final int SERVER_MAX_QUEUED_LINES = 256;
    /** Limits for a client's connection to its server: snapshots are large and arrive in bursts. */
    public static final int CLIENT_MAX_LINE_CHARS = 4 * 1024 * 1024;
    public static final int CLIENT_MAX_QUEUED_LINES = Integer.MAX_VALUE;

    private final Socket socket;
    private final BufferedWriter writer;
    private final BlockingQueue<String> incoming = new LinkedBlockingQueue<>();
    private final int maxLineChars;
    private final int maxQueuedLines;
    private final String clientAddress;
    private volatile boolean closed = false;

    /** A client's connection to a server it chose to connect to. */
    public NetworkConnection(Socket socket) throws IOException {
        this(socket, CLIENT_MAX_LINE_CHARS, CLIENT_MAX_QUEUED_LINES);
    }

    public NetworkConnection(Socket socket, int maxLineChars, int maxQueuedLines) throws IOException {
        this(socket, maxLineChars, maxQueuedLines, null);
    }

    /** clientAddress: the real client's address when a proxy in front reported it (PROXY protocol); null = the socket's own peer. */
    public NetworkConnection(Socket socket, int maxLineChars, int maxQueuedLines, String clientAddress) throws IOException {
        this.socket = socket;
        this.clientAddress = clientAddress;
        this.maxLineChars = maxLineChars;
        this.maxQueuedLines = maxQueuedLines;
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        Thread readerThread = new Thread(this::readLoop, "network-connection-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closed && (line = readBoundedLine(reader, maxLineChars)) != null) {
                if (incoming.size() >= maxQueuedLines) break;
                incoming.add(line);
            }
        } catch (IOException ignored) {
            // Socket closed/reset, or an over-long line — pollLine() simply stops receiving new lines.
        }
        // End of stream, or a peer over its limits: the connection is done. Lines already queued stay pollable.
        close();
    }

    /**
     * The next line without its terminator (\n or \r\n), or null at end of stream — like
     * BufferedReader.readLine(), but throws once a line passes maxChars instead of buffering it.
     */
    static String readBoundedLine(Reader reader, int maxChars) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') {
                int length = line.length();
                if (length > 0 && line.charAt(length - 1) == '\r') line.setLength(length - 1);
                return line.toString();
            }
            if (line.length() >= maxChars) throw new IOException("line longer than " + maxChars + " characters");
            line.append((char) c);
        }
        return line.length() > 0 ? line.toString() : null;
    }

    @Override
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
    @Override
    public String pollLine() {
        return incoming.poll();
    }

    public boolean isConnected() {
        return !closed && !socket.isClosed();
    }

    @Override
    public String peerAddress() {
        if (clientAddress != null) return clientAddress;
        java.net.InetAddress address = socket.getInetAddress();
        return address == null ? null : address.getHostAddress();
    }

    @Override
    public boolean isOpen() {
        return isConnected();
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
