package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.client.MatchClient;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickKind;
import io.github.elderpath_crusade.net.protocol.ClientMessage;
import io.github.elderpath_crusade.net.protocol.MatchView.PieceView;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import io.github.elderpath_crusade.net.transport.InProcessTransport;
import io.github.elderpath_crusade.net.transport.ServerConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.elderpath_crusade.server.MatchFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MatchServer with clients over the in-process transport (every message still goes through
 * the JSON codec). Replicas are checked against the server after every single tick.
 */
class MatchServerTest {
    private InProcessTransport transport;
    private MatchServer server;
    private final List<MatchClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        setUpContext();
        transport = new InProcessTransport();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
        tearDownContext();
    }

    private MatchClient join(SeatRequest seat, List<String> rawLog) {
        MatchClient client = new MatchClient(transport.connect(rawLog == null ? null : rawLog::add), seat, "test");
        clients.add(client);
        return client;
    }

    /** One server tick, every client polls, every seated replica must equal the server's view. */
    private void tick() {
        server.tick(TICK);
        for (MatchClient c : clients) {
            c.poll();
            if (c.getState().isWelcomed() && c.isConnected()) {
                assertConsistent(server, c);
                assertFalse(c.hasUnackedCommands(), "in process, a command is handled and acked within the tick");
            }
        }
    }

    private ServerMessage.Error refusalFor(ClientMessage.Hello hello) {
        ServerConnection raw = transport.connect();
        raw.send(hello);
        server.tick(TICK);
        ServerMessage message;
        ServerMessage.Error error = null;
        while ((message = raw.poll()) != null) if (message instanceof ServerMessage.Error e) error = e;
        assertFalse(raw.isOpen(), "a refused client is disconnected");
        return error;
    }

    @Test
    void twoHumans_scriptedMatch_replicasMatchTheServerAfterEveryTick_andHiddenInfoNeverLeaks() {
        server = new MatchServer(config(deckOf("Zapper", 12), deckOf("Runner", 12), Map.of()), transport);
        List<String> p1Raw = new ArrayList<>();
        List<String> p2Raw = new ArrayList<>();
        MatchClient p1 = join(SeatRequest.P1, p1Raw);
        MatchClient p2 = join(SeatRequest.P2, p2Raw);

        new ScriptedMatch(p1, p2, this::tick).play();

        assertNoForeignCardLeak(p2Raw, "P1");
        assertNoForeignCardLeak(p1Raw, "P2");
        assertTrue(p2Raw.stream().anyMatch(l -> l.contains("\"CardPlayedEvent\"") && l.contains("Zapper")),
                "playing a card is public");
        assertTrue(server.isEnded());
        assertEquals(PieceAlignment.P1, server.getWinner());
    }

    @Test
    void handshake_seatsByRequest_andRefusesMismatchedVersionTakenSeatAndFullMatch() {
        server = new MatchServer(config(deckOf("Runner", 12), deckOf("Runner", 12), Map.of()), transport);

        ServerMessage.Error version = refusalFor(new ClientMessage.Hello(999, SeatRequest.ANY, "old"));
        assertEquals(ServerMessage.Error.Code.VERSION_MISMATCH, version.code());

        MatchClient p2 = join(SeatRequest.P2, null);
        tick();
        assertEquals(PieceAlignment.P2, p2.getState().getSeat());
        assertFalse(server.isStarted(), "waits for every human seat");

        ServerMessage.Error taken = refusalFor(new ClientMessage.Hello(
                io.github.elderpath_crusade.net.protocol.MessageCodec.PROTOCOL_VERSION, SeatRequest.P2, "dup"));
        assertEquals(ServerMessage.Error.Code.SEAT_TAKEN, taken.code());

        MatchClient any = join(SeatRequest.ANY, null);
        tick();
        assertEquals(PieceAlignment.P1, any.getState().getSeat(), "ANY gets the free seat");
        assertTrue(server.isStarted());

        ServerMessage.Error full = refusalFor(new ClientMessage.Hello(
                io.github.elderpath_crusade.net.protocol.MessageCodec.PROTOCOL_VERSION, SeatRequest.ANY, "late"));
        assertEquals(ServerMessage.Error.Code.MATCH_FULL, full.code());
    }

    @Test
    void solo_humanVsServerSideBot_botPlaysItsTurns_replicaStaysConsistent() {
        server = new MatchServer(config(deckOf("Runner", 12), deckOf("Runner", 12), Map.of(PieceAlignment.P2, BotKind.SMART)),
                transport);
        MatchClient human = join(SeatRequest.ANY, null);
        tick();
        assertEquals(PieceAlignment.P1, human.getState().getSeat());
        assertTrue(server.isStarted(), "solo starts as soon as its one human joins");
        assertEquals("BOT_SMART", human.getState().getSlots().get(PieceAlignment.P2));

        for (int turn = 0; turn < 4 && !server.isEnded(); turn++) {
            assertEquals(PieceAlignment.P1, human.getState().getCurrentPlayer());
            human.playSummon(firstCard(human), 0, turn);
            tick();
            human.endTurn();
            tick();
            int ticks = 0;
            while (human.getState().getCurrentPlayer() == PieceAlignment.P2 && !server.isEnded()) {
                tick();
                assertTrue(++ticks < 6000, "the bot ends its turn");
            }
        }
        assertTrue(human.getState().getPieces().stream().anyMatch(p -> p.owner() == PieceAlignment.P2),
                "the bot summoned, and the human sees it");
    }

    @Test
    void botVsBot_withNoClients_playsToAWinner() {
        server = new MatchServer(config(deckOf("Runner", 12), deckOf("Runner", 12),
                Map.of(PieceAlignment.P1, BotKind.SMART, PieceAlignment.P2, BotKind.SMART)), transport);
        int ticks = 0;
        while (!server.isEnded()) {
            server.tick(0.1f);
            assertTrue(++ticks < 20_000, "bot vs bot finishes");
        }
        assertNotNull(server.getWinner());
    }

    @Test
    void rejoiningAStartedMatch_getsASnapshotThatMatchesTheServer() {
        server = new MatchServer(config(deckOf("Zapper", 12), deckOf("Runner", 12), Map.of()), transport);
        MatchClient p1 = join(SeatRequest.P1, null);
        MatchClient p2 = join(SeatRequest.P2, null);
        tick();
        p1.playSummon(firstCard(p1), 0, 2);
        tick();
        p1.endTurn();
        tick();
        p2.playSummon(firstCard(p2), 6, 2);
        tick();

        p2.leave();
        tick();
        assertFalse(server.isConnected(PieceAlignment.P2));
        clients.remove(p2);

        MatchClient back = join(SeatRequest.P2, null);
        server.tick(TICK);
        back.poll();
        assertConsistent(server, back);
        assertEquals(2, back.getState().getOwnHand().size(), "its own hand, by name");
        assertEquals(2, back.getState().getPieces().size());

        back.endTurn(); // and play on
        tick();
        assertEquals(PieceAlignment.P1, back.getState().getCurrentPlayer());
    }

    @Test
    void abilitySummonedPiece_appearsInBothReplicas() { // BUG-025
        server = new MatchServer(config(deckOf("Conjurer", 12), deckOf("Runner", 12), Map.of()), transport);
        MatchClient p1 = join(SeatRequest.P1, null);
        MatchClient p2 = join(SeatRequest.P2, null);
        tick();
        p1.playSummon(firstCard(p1), 0, 0);
        tick();
        p1.endTurn();
        tick();
        p2.endTurn();
        tick();

        String conjurer = p1.getState().getPieceAt(0, 0).id();
        p1.activateAbility(conjurer, 0);
        tick();
        assertEquals(PickKind.TILE, p1.getState().getPendingPick().kind());
        p1.answerPick(new Coord(0, 4));
        tick();

        PieceView summoned = p2.getState().getPieceAt(0, 4);
        assertNotNull(summoned, "the opponent sees the effect-summoned piece");
        assertEquals("Runner", summoned.registryKey());
        assertEquals(3, summoned.currentHealth());
        assertEquals(summoned, p1.getState().getPieceAt(0, 4));
    }

    @Test
    void ownerCancelsAPick_noEffectAndNoCost() {
        server = new MatchServer(config(deckOf("Zapper", 12), deckOf("Runner", 12), Map.of()), transport);
        MatchClient p1 = join(SeatRequest.P1, null);
        MatchClient p2 = join(SeatRequest.P2, null);
        tick();
        p1.playSummon(firstCard(p1), 0, 0);
        tick();
        p1.endTurn();
        tick();
        p2.playSummon(firstCard(p2), 6, 0);
        tick();
        p2.endTurn();
        tick();
        p1.moveOrAttack(0, 0, 2, 0);
        tick();
        p1.endTurn();
        tick();
        p2.moveOrAttack(6, 0, 4, 0);
        tick();
        p2.endTurn();
        tick();

        p1.activateAbility(p1.getState().getPieceAt(2, 0).id(), 0);
        tick();
        assertNotNull(p1.getState().getPendingPick());
        p1.cancelPick();
        tick();
        assertNull(p1.getState().getPendingPick(), "cancelled: no cost, no effect");
        assertEquals(3, p2.getState().getPieceAt(4, 0).currentHealth());
        assertEquals(2, p1.getState().getMana(PieceAlignment.P1), "the mana cost wasn't paid");
    }

    @Test
    void sanity_contextIsHeadless() {
        assertTrue(GameContext.get().isHeadless());
    }
}
