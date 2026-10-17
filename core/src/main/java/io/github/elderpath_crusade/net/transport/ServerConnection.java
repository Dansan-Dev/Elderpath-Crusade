package io.github.elderpath_crusade.net.transport;

import io.github.elderpath_crusade.net.protocol.ClientMessage;
import io.github.elderpath_crusade.net.protocol.ServerMessage;

/** A client's end of its connection to a match server. Polled from the client's own thread. */
public interface ServerConnection {
    void send(ClientMessage message);

    /**
     * The next message from the server, or null if none has arrived.
     *
     * @throws io.github.elderpath_crusade.net.protocol.ProtocolException for a line that isn't a valid message
     */
    ServerMessage poll();

    boolean isOpen();

    void close();
}
