package io.github.elderpath_crusade.net.transport;

/**
 * A bidirectional, newline-framed text link — a TCP socket (NetworkConnection) or an
 * in-process queue pair. Received lines queue up and are drained from the polling thread;
 * lines already received stay pollable after the link closes.
 */
public interface LineLink {
    void send(String line);

    /** Non-blocking; null if nothing has arrived. */
    String pollLine();

    boolean isOpen();

    /** The remote address as text (what per-client limits are keyed by), or null if the link has none (in-process). */
    default String peerAddress() {
        return null;
    }

    void close();
}
