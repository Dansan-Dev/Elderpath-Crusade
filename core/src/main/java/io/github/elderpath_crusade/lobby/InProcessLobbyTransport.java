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

    /** A new client connection to this transport's server. */
    public synchronized LineLink connect() {
        InProcessLink[] pair = InProcessLink.pair();
        links.add(pair[0]);
        accepted.add(pair[0]);
        return pair[1];
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
