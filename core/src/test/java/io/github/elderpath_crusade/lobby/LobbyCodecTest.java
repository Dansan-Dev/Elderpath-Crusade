package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.lobby.LobbyClientMessage.*;
import io.github.elderpath_crusade.lobby.LobbyServerMessage.*;
import io.github.elderpath_crusade.net.protocol.ProtocolException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LobbyCodecTest {
    private static final LobbyView VIEW = new LobbyView("ab12cd34", "Casual", List.of("ann", "bob"), true);

    @Test
    void everyClientMessage_roundTrips() {
        List<LobbyClientMessage> messages = List.of(
                new LobbyHello(LobbyCodec.LOBBY_PROTOCOL_VERSION, "ann"),
                new ListLobbies(),
                new CreateLobby("Casual", "pw", List.of("Wolf", "Wolf Cub")),
                new CreateLobby("Open", null, List.of("Wolf")),
                new CreateLobby("Cards", null, List.of("Wolf"), LobbyDeckSource.COLLECTION),
                new JoinLobby("ab12cd34", "pw", List.of("Wolf")),
                new LeaveLobby(),
                new StartMatch(),
                new RejoinMatch("ab12cd34", "feedface"));
        for (LobbyClientMessage m : messages) assertEquals(m, LobbyCodec.decodeClient(LobbyCodec.encode(m)));
    }

    @Test
    void everyServerMessage_roundTrips() {
        List<LobbyServerMessage> messages = List.of(
                new LobbyWelcome(1),
                new LobbyList(List.of(VIEW)),
                new LobbyList(List.of()),
                new LobbyJoined(VIEW, true),
                new LobbyUpdate(VIEW),
                new LobbyUpdate(new LobbyView("cd34ab12", "Decks", List.of("ann"), false, LobbyDeckSource.COLLECTION)),
                new LobbyClosed("The host left"),
                new MatchStarting("P2"),
                new MatchStarting("P1", "0123abcd"),
                new MatchStarting("P2", "0123abcd", "Yxaria"),
                new LobbyError(LobbyError.Code.WRONG_PASSWORD, "Wrong password"));
        for (LobbyServerMessage m : messages) assertEquals(m, LobbyCodec.decodeServer(LobbyCodec.encode(m)));
    }

    @Test
    void malformedLines_areProtocolErrors() {
        for (String line : List.of("", "not json", "{}", "{\"type\":\"CreateLobby\"}",
                "{\"type\":\"CreateLobby\",\"payload\":null}", "{\"type\":\"Nope\",\"payload\":{}}",
                "{\"type\":\"LobbyHello\",\"payload\":{\"protocolVersion\":1,\"playerName\":\"a\",\"isAdmin\":true}}",
                "{\"type\":\"JoinLobby\",\"payload\":{\"lobbyId\":{\"a\":1}}}")) {
            assertThrows(ProtocolException.class, () -> LobbyCodec.decodeClient(line), line);
        }
    }

    @Test
    void deckSource_defaultsToDraft_andRejectsUnknownValues() {
        assertEquals(LobbyDeckSource.DRAFT, new CreateLobby("x", null, List.of("Wolf")).deckSource());
        assertEquals(LobbyDeckSource.DRAFT, new CreateLobby("x", null, List.of("Wolf"), null).deckSource());
        assertEquals(LobbyDeckSource.DRAFT, new LobbyView("a", "n", List.of(), false).deckSource());
        // a message without the field (an older client, or a hand-written one) is read as DRAFT; an invented value is an error
        LobbyClientMessage noField = LobbyCodec.decodeClient(
                "{\"type\":\"CreateLobby\",\"payload\":{\"name\":\"n\",\"password\":null,\"deck\":[\"Wolf\"]}}");
        assertEquals(LobbyDeckSource.DRAFT, ((CreateLobby) noField).deckSource());
        assertThrows(ProtocolException.class, () -> LobbyCodec.decodeClient(
                "{\"type\":\"CreateLobby\",\"payload\":{\"name\":\"n\",\"deck\":[\"Wolf\"],\"deckSource\":\"ANYTHING\"}}"));
    }

    @Test
    void protocolVersion_is3() {
        assertEquals(3, LobbyCodec.LOBBY_PROTOCOL_VERSION);
    }

    @Test
    void anErrorQuotesOnlyAPrefixOfALongLine() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> LobbyCodec.decodeClient("x".repeat(5_000)));
        assertTrue(e.getMessage().length() < 300);
    }
}
