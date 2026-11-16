package io.github.elderpath_crusade.lobby;

import java.util.List;

/** What the lobby service tells a client. */
public sealed interface LobbyServerMessage {

    record LobbyWelcome(int protocolVersion) implements LobbyServerMessage {}

    /** An open lobby as a browsing player sees it. members are player names in seat order (host first). */
    record LobbyView(String id, String name, List<String> members, boolean passwordProtected) {}

    record LobbyList(List<LobbyView> lobbies) implements LobbyServerMessage {}

    /** You are in this lobby (sent on create/join); host = whether you may start the match. */
    record LobbyJoined(LobbyView lobby, boolean host) implements LobbyServerMessage {}

    /** Someone joined or left your lobby. */
    record LobbyUpdate(LobbyView lobby) implements LobbyServerMessage {}

    /** Your lobby is gone (the host left, it timed out, ...). You are out of it. */
    record LobbyClosed(String reason) implements LobbyServerMessage {}

    /**
     * The match is ready (or, answering RejoinMatch, resumed): from the next line on this
     * connection carries the match protocol, so send the match Hello for the seat ("P1" or "P2")
     * now. No more lobby messages either way. rejoinToken is the secret to present in a
     * RejoinMatch if this connection drops while the match is still running.
     */
    record MatchStarting(String seat, String rejoinToken) implements LobbyServerMessage {
        public MatchStarting(String seat) {
            this(seat, null);
        }
    }

    record LobbyError(Code code, String message) implements LobbyServerMessage {
        public enum Code {
            BAD_MESSAGE, VERSION_MISMATCH, NOT_HELLOED, INVALID_NAME, INVALID_DECK,
            LOBBY_NOT_FOUND, LOBBY_FULL, LOBBY_STARTED, WRONG_PASSWORD, ALREADY_IN_LOBBY, NOT_IN_LOBBY,
            NOT_HOST, NEED_TWO_PLAYERS, SERVER_BUSY, TOO_MANY_LOBBIES, START_FAILED, TOO_MANY_ATTEMPTS, REJOIN_REFUSED, IDLE_TIMEOUT
        }
    }
}
