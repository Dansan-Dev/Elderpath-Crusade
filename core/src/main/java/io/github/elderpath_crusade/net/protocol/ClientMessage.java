package io.github.elderpath_crusade.net.protocol;

import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;

/** Client → match server. */
public sealed interface ClientMessage permits
        ClientMessage.Hello,
        ClientMessage.Command,
        ClientMessage.Leave {

    /** First message on a connection: asks for a seat. Answered by Welcome + Snapshot, or Error. */
    record Hello(int protocolVersion, SeatRequest seat, String playerName) implements ClientMessage {}

    /**
     * A requested action. seq is chosen by the client (increasing) and echoed in Rejected,
     * so it can tell which request failed. The acting side is the connection's seat, never
     * anything in the command.
     */
    record Command(long seq, NetworkCommand command) implements ClientMessage {}

    /** The client is leaving; its seat stays reserved for it. */
    record Leave() implements ClientMessage {}
}
