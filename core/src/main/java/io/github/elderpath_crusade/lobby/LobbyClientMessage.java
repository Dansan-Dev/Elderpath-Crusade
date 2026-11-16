package io.github.elderpath_crusade.lobby;

import java.util.List;

/**
 * What a client says to the lobby service. Lobby messages are one JSON line each, like the match
 * protocol; the very first must be LobbyHello. The service never takes the player's identity from
 * anywhere but that hello, and never trusts a deck: it validates every card name.
 */
public sealed interface LobbyClientMessage {

    /** First message on a connection. playerName is shown to the other players. */
    record LobbyHello(int protocolVersion, String playerName) implements LobbyClientMessage {}

    /** Asks for the open lobbies; answered by LobbyList. */
    record ListLobbies() implements LobbyClientMessage {}

    /** Creates a lobby with this player as host. password null or empty = no password. */
    record CreateLobby(String name, String password, List<String> deck) implements LobbyClientMessage {}

    /** Joins an open lobby. password is required exactly when the lobby is password protected. */
    record JoinLobby(String lobbyId, String password, List<String> deck) implements LobbyClientMessage {}

    /** Leaves the current lobby (the host leaving closes it). */
    record LeaveLobby() implements LobbyClientMessage {}

    /** Host only, with a guest present: starts the match. */
    record StartMatch() implements LobbyClientMessage {}
}
