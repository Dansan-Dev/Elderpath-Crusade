package io.github.elderpath_crusade.net.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Clients in the same JVM as the match server (tests, and potentially a client hosting its
 * own server thread). Every message still goes through MessageCodec, like TCP.
 */
public final class InProcessTransport implements ServerTransport {
    private final Queue<ClientChannel> accepted = new ConcurrentLinkedQueue<>();
    private final List<LineLink> links = new ArrayList<>();

    /** Opens a new client connection to this transport's server. */
    public ServerConnection connect() {
        return connect(null);
    }

    public synchronized ServerConnection connect(Consumer<String> rawTap) {
        InProcessLink[] pair = InProcessLink.pair();
        links.add(pair[0]);
        accepted.add(LineChannels.clientChannel(pair[0]));
        return LineChannels.serverConnection(pair[1], rawTap);
    }

    @Override
    public ClientChannel accept() {
        return accepted.poll();
    }

    @Override
    public synchronized void close() {
        for (LineLink link : links) link.close();
        links.clear();
    }
}
