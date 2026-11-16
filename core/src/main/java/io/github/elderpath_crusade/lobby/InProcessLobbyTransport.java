package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.net.transport.InProcessLink;
import io.github.elderpath_crusade.net.transport.LineLink;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Lobby clients in the same JVM (tests). */
public final class InProcessLobbyTransport implements LobbyTransport {
    private final Queue<LineLink> accepted = new ConcurrentLinkedQueue<>();
    private final List<LineLink> links = new ArrayList<>();

    /** A new client connection to this transport's server (which sees no peer address). */
    public synchronized LineLink connect() {
        return connect(null);
    }

    /** A new client connection the server sees as coming from peerAddress (to test per-address limits). */
    public synchronized LineLink connect(String peerAddress) {
        InProcessLink[] pair = InProcessLink.pair();
        links.add(pair[0]);
        accepted.add(peerAddress == null ? pair[0] : withPeer(pair[0], peerAddress));
        return pair[1];
    }

    private static LineLink withPeer(LineLink link, String peerAddress) {
        return new LineLink() {
            @Override public void send(String line) { link.send(line); }
            @Override public String pollLine() { return link.pollLine(); }
            @Override public boolean isOpen() { return link.isOpen(); }
            @Override public void close() { link.close(); }
            @Override public String peerAddress() { return peerAddress; }
        };
    }

    @Override
    public LineLink accept() {
        return accepted.poll();
    }

    @Override
    public synchronized void close() {
        for (LineLink link : links) link.close();
        links.clear();
    }
}
