package io.github.elderpath_crusade.net.transport;

/** Where a match server's clients come from: yields newly connected channels to the server's tick. */
public interface ServerTransport {
    /** The next newly connected client, or null if none is waiting. Non-blocking. */
    ClientChannel accept();

    /** Stops accepting and closes the connections it accepted. */
    void close();
}
