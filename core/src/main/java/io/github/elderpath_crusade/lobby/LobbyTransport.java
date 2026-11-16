package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.net.transport.LineLink;

/** Where the lobby service's clients come from: yields newly connected links to its tick. */
public interface LobbyTransport {
    /** The next newly connected client, or null. Non-blocking. */
    LineLink accept();

    /** Stops accepting and closes the connections it accepted. */
    void close();
}
