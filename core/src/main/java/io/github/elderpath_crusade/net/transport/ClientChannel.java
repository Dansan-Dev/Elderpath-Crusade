package io.github.elderpath_crusade.net.transport;

import io.github.elderpath_crusade.net.protocol.ClientMessage;
import io.github.elderpath_crusade.net.protocol.ServerMessage;

/** The match server's end of one client's connection. Polled from the server's tick thread. */
public interface ClientChannel {
    void send(ServerMessage message);

    /**
     * The next message from the client, or null if none has arrived.
     *
     * @throws io.github.elderpath_crusade.net.protocol.ProtocolException for a line that isn't a valid message
     */
    ClientMessage poll();

    boolean isOpen();

    void close();
}
