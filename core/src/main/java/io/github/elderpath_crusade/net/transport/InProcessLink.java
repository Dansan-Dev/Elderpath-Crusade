package io.github.elderpath_crusade.net.transport;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One end of an in-process LineLink pair: what one end sends, the other polls. Closing either
 * end closes both, like a socket.
 */
final class InProcessLink implements LineLink {
    private final BlockingQueue<String> incoming;
    private final BlockingQueue<String> outgoing;
    private final AtomicBoolean closed;

    private InProcessLink(BlockingQueue<String> incoming, BlockingQueue<String> outgoing, AtomicBoolean closed) {
        this.incoming = incoming;
        this.outgoing = outgoing;
        this.closed = closed;
    }

    /** Two connected ends: [0] and [1]. */
    static InProcessLink[] pair() {
        BlockingQueue<String> aToB = new LinkedBlockingQueue<>();
        BlockingQueue<String> bToA = new LinkedBlockingQueue<>();
        AtomicBoolean closed = new AtomicBoolean(false);
        return new InProcessLink[]{new InProcessLink(bToA, aToB, closed), new InProcessLink(aToB, bToA, closed)};
    }

    @Override
    public void send(String line) {
        if (!closed.get()) outgoing.add(line);
    }

    @Override
    public String pollLine() {
        return incoming.poll();
    }

    @Override
    public boolean isOpen() {
        return !closed.get();
    }

    @Override
    public void close() {
        closed.set(true);
    }
}
