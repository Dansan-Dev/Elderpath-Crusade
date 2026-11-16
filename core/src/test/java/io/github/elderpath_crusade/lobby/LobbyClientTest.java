package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.lobby.LobbyServerMessage.LobbyError;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.net.transport.InProcessTransport;
import io.github.elderpath_crusade.net.transport.LineLink;
import io.github.elderpath_crusade.server.MatchServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.github.elderpath_crusade.server.MatchFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Two LobbyClients, the LobbyServer, and a real in-process MatchServer behind the bridge:
 * host, join (with a password), start, then both players are seated through the same
 * connections with ClientMatchSession.joinThrough.
 */
class LobbyClientTest {
    private static final List<String> DECK = deckOf("Runner", 12);

    private InProcessLobbyTransport lobbyTransport;
    private InProcessTransport matchTransport;
    private MatchServer match;
    private LobbyServer lobby;

    @BeforeEach
    void setUp() {
        setUpContext();
        lobbyTransport = new InProcessLobbyTransport();
        matchTransport = new InProcessTransport();
        match = new MatchServer(config(DECK, DECK, Map.of()), matchTransport);
        MatchLauncher launcher = spec -> CompletableFuture.completedFuture(new MatchLauncher.MatchEndpoint() {
            @Override
            public LineLink connect() {
                return matchTransport.connectRaw();
            }

            @Override
            public void close() {
            }
        });
        lobby = new LobbyServer(lobbyTransport, launcher, 2, card -> card.equals("Runner"));
    }

    @AfterEach
    void tearDown() {
        lobby.close();
        match.close();
        tearDownContext();
    }

    private void tick(LobbyClient... clients) {
        lobby.tick();
        match.tick(TICK);
        for (LobbyClient c : clients) c.poll();
    }

    private void tick(ClientMatchSession a, ClientMatchSession b) {
        lobby.tick();
        match.tick(TICK);
        a.poll();
        b.poll();
    }

    @Test
    void hostJoinStart_andBothPlayersEndUpPlayingThroughTheBridge() {
        LobbyClient host = new LobbyClient(lobbyTransport.connect(), "host");
        LobbyClient guest = new LobbyClient(lobbyTransport.connect(), "guest");
        tick(host, guest);
        assertTrue(host.isWelcomed());

        host.createLobby("E2E", "pw", DECK);
        tick(host, guest);
        assertTrue(host.isHost());
        assertEquals("E2E", host.getLobby().name());

        guest.requestList();
        tick(host, guest);
        assertEquals(1, guest.getLobbies().size());
        String id = guest.getLobbies().get(0).id();

        guest.joinLobby(id, "nope", DECK);
        tick(host, guest);
        assertEquals(LobbyError.Code.WRONG_PASSWORD, guest.getLastError().code());
        assertNull(guest.getLobby());
        guest.joinLobby(id, "pw", DECK);
        tick(host, guest);
        tick(host, guest);
        assertNull(guest.getLastError());
        assertFalse(guest.isHost());
        assertEquals(List.of("host", "guest"), host.getLobby().members(), "the host sees the guest arrive");

        host.startMatch();
        tick(host, guest);
        tick(host, guest);
        assertEquals("P1", host.getMatchSeat());
        assertEquals("P2", guest.getMatchSeat());

        ClientMatchSession hostSession = ClientMatchSession.joinThrough(host.takeMatchConnection(), SeatRequest.P1, "host");
        ClientMatchSession guestSession = ClientMatchSession.joinThrough(guest.takeMatchConnection(), SeatRequest.P2, "guest");
        for (int i = 0; i < 20 && !(hostSession.getStatus().isReady() && guestSession.getStatus().isReady()); i++) {
            tick(hostSession, guestSession);
        }
        assertTrue(hostSession.getStatus().isReady(), String.valueOf(hostSession.getStatus()));
        assertTrue(guestSession.getStatus().isReady(), String.valueOf(guestSession.getStatus()));
        assertEquals(PieceAlignment.P1, hostSession.viewState().getSeat());
        assertEquals(PieceAlignment.P2, guestSession.viewState().getSeat());
        assertFalse(hostSession.viewState().getOwnHand().isEmpty(), "a hand was dealt");
        hostSession.close();
        guestSession.close();
    }

    @Test
    void aClosedLobbyIsReportedToTheGuest() {
        LobbyClient host = new LobbyClient(lobbyTransport.connect(), "host");
        LobbyClient guest = new LobbyClient(lobbyTransport.connect(), "guest");
        tick(host, guest);
        host.createLobby("Gone", null, DECK);
        tick(host, guest);
        String id = host.getLobby().id();
        guest.joinLobby(id, null, DECK);
        tick(host, guest);
        tick(host, guest);
        host.leaveLobby();
        tick(host, guest);
        tick(host, guest);
        assertNull(guest.getLobby());
        assertEquals("The host left", guest.getClosedReason());
    }

    @Test
    void takingTheMatchConnectionBeforeAMatchStartsIsAnError() {
        LobbyClient c = new LobbyClient(lobbyTransport.connect(), "ann");
        tick(c);
        assertThrows(IllegalStateException.class, c::takeMatchConnection);
    }

    @Test
    void unreadableServerOutputClosesTheClient() {
        LineLink fake = new LineLink() {
            boolean open = true;
            final java.util.ArrayDeque<String> lines = new java.util.ArrayDeque<>(List.of("not a lobby message"));

            @Override public void send(String line) {}
            @Override public String pollLine() { return lines.poll(); }
            @Override public boolean isOpen() { return open; }
            @Override public void close() { open = false; }
        };
        LobbyClient broken = new LobbyClient(fake, "bob");
        broken.poll();
        assertFalse(broken.isConnected());
        assertEquals(LobbyError.Code.BAD_MESSAGE, broken.getLastError().code());
    }
}
