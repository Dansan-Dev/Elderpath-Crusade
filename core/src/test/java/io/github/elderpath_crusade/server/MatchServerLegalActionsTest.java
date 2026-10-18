package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.client.MatchClient;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import io.github.elderpath_crusade.net.transport.InProcessTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.elderpath_crusade.server.MatchFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** How MatchServer delivers LegalActions: to the right seat only, on change, and on (re)joining. */
class MatchServerLegalActionsTest {
    private InProcessTransport transport;
    private MatchServer server;
    private final List<MatchClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        setUpContext();
        transport = new InProcessTransport();
        server = new MatchServer(config(deckOf("Runner", 12), deckOf("Runner", 12), Map.of()), transport);
    }

    @AfterEach
    void tearDown() {
        server.close();
        tearDownContext();
    }

    private MatchClient join(SeatRequest seat, List<String> raw) {
        MatchClient client = new MatchClient(transport.connect(raw::add), seat, "test");
        clients.add(client);
        return client;
    }

    private void tick() {
        server.tick(TICK);
        for (MatchClient c : clients) {
            c.poll();
            if (c.getState().isWelcomed() && c.isConnected()) assertConsistent(server, c);
        }
    }

    private static List<LegalActions> received(List<String> raw) {
        List<LegalActions> out = new ArrayList<>();
        for (String line : raw) {
            if (MessageCodec.decodeServer(line) instanceof ServerMessage.LegalActionsUpdate u) out.add(u.actions());
        }
        return out;
    }

    @Test
    void onlyTheActingSeatGetsOptions_andEachSeatOnlyEverGetsItsOwn() {
        List<String> p1Raw = new ArrayList<>();
        List<String> p2Raw = new ArrayList<>();
        MatchClient p1 = join(SeatRequest.P1, p1Raw);
        MatchClient p2 = join(SeatRequest.P2, p2Raw);
        tick();

        LegalActions p1Legal = p1.getState().getLegalActions();
        assertTrue(p1Legal.yourTurn() && p1Legal.canEndTurn());
        assertFalse(p1Legal.cards().isEmpty(), "P1 can summon on its first turn");
        assertEquals(LegalActions.none(PieceAlignment.P2), p2.getState().getLegalActions());

        // P1 summons, ends its turn; P2's turn
        LegalActions.CardActions card = p1Legal.cards().get(0);
        Coord tile = card.targets().get(0);
        p1.playSummon(card.instanceId(), tile.row(), tile.col());
        tick();
        assertNull(p1.getState().getLegalActions().forCard(card.instanceId()), "a played card is no longer offered");
        p1.endTurn();
        tick();
        assertEquals(LegalActions.none(PieceAlignment.P1), p1.getState().getLegalActions());
        assertTrue(p2.getState().getLegalActions().yourTurn());

        for (LegalActions l : received(p1Raw)) assertEquals(PieceAlignment.P1, l.seat());
        for (LegalActions l : received(p2Raw)) assertEquals(PieceAlignment.P2, l.seat());
    }

    @Test
    void sentOnlyWhenTheyChange() {
        List<String> p1Raw = new ArrayList<>();
        MatchClient p1 = join(SeatRequest.P1, p1Raw);
        join(SeatRequest.P2, new ArrayList<>());
        tick();
        int before = received(p1Raw).size();
        for (int i = 0; i < 30; i++) tick();
        assertEquals(before, received(p1Raw).size(), "nothing changed, nothing resent");

        p1.endTurn();
        tick();
        assertEquals(before + 1, received(p1Raw).size(), "ending the turn changes them once");
    }

    @Test
    void aRejoiningClientGetsThemAfterItsSnapshot() {
        MatchClient p1 = join(SeatRequest.P1, new ArrayList<>());
        MatchClient p2 = join(SeatRequest.P2, new ArrayList<>());
        tick();
        p1.leave();
        clients.remove(p1);
        tick();

        List<String> raw = new ArrayList<>();
        MatchClient back = join(SeatRequest.P1, raw);
        tick();
        assertEquals(server.legalActionsFor(PieceAlignment.P1), back.getState().getLegalActions());
        assertTrue(back.getState().getLegalActions().yourTurn());
        int snapshot = -1;
        int legal = -1;
        for (int i = 0; i < raw.size(); i++) {
            ServerMessage m = MessageCodec.decodeServer(raw.get(i));
            if (m instanceof ServerMessage.Snapshot && snapshot < 0) snapshot = i;
            if (m instanceof ServerMessage.LegalActionsUpdate && legal < 0) legal = i;
        }
        assertTrue(snapshot >= 0 && legal > snapshot, "LegalActions follow the Snapshot");
        assertNotNull(p2.getState().getLegalActions());
    }

    @Test
    void nothingBeforeTheMatchBegins_orAfterItEnds() {
        MatchClient p1 = join(SeatRequest.P1, new ArrayList<>());
        tick();
        assertFalse(server.isStarted());
        assertEquals(LegalActions.none(PieceAlignment.P1), p1.getState().getLegalActions());
    }
}
