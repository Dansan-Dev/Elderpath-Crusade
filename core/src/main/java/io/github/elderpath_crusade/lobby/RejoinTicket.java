package io.github.elderpath_crusade.lobby;

/**
 * Everything a client needs to get back into a match it was bridged to through the lobby
 * service after its connection dropped: where the service is, which lobby's match, the secret
 * from MatchStarting, the seat it held, the name it plays under, and the opponent's display name (for the result
 * screen after a reconnect).
 */
public record RejoinTicket(LobbyEndpoint endpoint, String lobbyId, String rejoinToken, String seat, String playerName,
                           String opponentName) {
    public RejoinTicket(LobbyEndpoint endpoint, String lobbyId, String rejoinToken, String seat, String playerName) {
        this(endpoint, lobbyId, rejoinToken, seat, playerName, null);
    }
}
