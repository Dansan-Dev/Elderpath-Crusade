package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.client.MatchClient;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.net.protocol.MatchView;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.net.transport.TcpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.github.elderpath_crusade.server.MatchFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The same matches over real localhost TCP (ephemeral port). Delivery isn't instant, so each
 * step ticks and polls until both replicas match the server, with a deadline.
 */
@Timeout(30)
class MatchServerTcpTest {
    private static final long STEP_DEADLINE_MS = 10_000;

    private TcpTransport transport;
    private MatchServer server;
    private final List<MatchClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        setUpContext();
        transport = new TcpTransport("127.0.0.1", 0);
    }

    @AfterEach
    void tearDown() {
        for (MatchClient c : clients) c.close();
        if (server != null) server.close();
        tearDownContext();
    }

    private MatchClient join(SeatRequest seat, List<String> rawLog) throws Exception {
        MatchClient client = new MatchClient(
                TcpTransport.connect("127.0.0.1", transport.getPort(), rawLog::add), seat, "tcp");
        clients.add(client);
        return client;
    }

    /**
     * Ticks and polls until every client is seated, has every command it sent acknowledged
     * (an Ack follows everything its command caused — quiet rounds alone can't tell a command
     * still in flight on the socket from one that was never sent), and agrees with the server
     * for two quiet rounds in a row.
     */
    private void settle() {
        long deadline = System.nanoTime() + STEP_DEADLINE_MS * 1_000_000L;
        int agreeingRounds = 0;
        List<String> lastDiff = List.of();
        while (agreeingRounds < 2) {
            server.tick(TICK);
            int received = 0;
            for (MatchClient c : clients) received += c.poll();
            lastDiff = diffs();
            for (MatchClient c : clients) if (c.hasUnackedCommands()) lastDiff.add(c.getState().getSeat() + " has unacknowledged commands");
            agreeingRounds = lastDiff.isEmpty() && received == 0 ? agreeingRounds + 1 : 0;
            if (System.nanoTime() - deadline > 0) fail("replicas never settled: " + lastDiff);
            if (agreeingRounds < 2) sleepBriefly();
        }
    }

    private List<String> diffs() {
        List<String> out = new ArrayList<>();
        for (MatchClient c : clients) {
            if (!c.getState().isWelcomed()) {
                out.add("a client isn't seated yet");
                continue;
            }
            out.addAll(MatchView.diff(server.viewFor(c.getState().getSeat()), c.getState().toView()));
        }
        return out;
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void twoHumans_scriptedMatchOverTcp() throws Exception {
        server = new MatchServer(config(deckOf("Zapper", 12), deckOf("Runner", 12), Map.of()), transport);
        // Appended by each connection's reader thread
        List<String> p1Raw = new CopyOnWriteArrayList<>();
        List<String> p2Raw = new CopyOnWriteArrayList<>();
        MatchClient p1 = join(SeatRequest.P1, p1Raw);
        MatchClient p2 = join(SeatRequest.P2, p2Raw);

        new ScriptedMatch(p1, p2, this::settle).play();

        assertNoForeignCardLeak(p2Raw, "P1");
        assertNoForeignCardLeak(p1Raw, "P2");
        assertEquals(PieceAlignment.P1, server.getWinner());
    }

    @Test
    void solo_humanVsBotOverTcp() throws Exception {
        server = new MatchServer(config(deckOf("Runner", 12), deckOf("Runner", 12), Map.of(PieceAlignment.P2, BotKind.BASIC)),
                transport);
        MatchClient human = join(SeatRequest.ANY, new ArrayList<>());
        settle();
        assertTrue(server.isStarted());
        human.playSummon(firstCard(human), 0, 2);
        settle();
        human.endTurn();
        long deadline = System.nanoTime() + STEP_DEADLINE_MS * 1_000_000L;
        do {
            server.tick(0.1f);
            human.poll();
            sleepBriefly();
            assertTrue(System.nanoTime() - deadline < 0, "the bot finished its turn");
        } while (human.getState().getCurrentPlayer() != PieceAlignment.P1);
        settle();
    }

    @Test
    void disconnectingClient_seatIsReserved_andCanBeRetaken() throws Exception {
        server = new MatchServer(config(deckOf("Runner", 12), deckOf("Runner", 12), Map.of()), transport);
        MatchClient p1 = join(SeatRequest.P1, new ArrayList<>());
        MatchClient p2 = join(SeatRequest.P2, new ArrayList<>());
        settle();
        p1.playSummon(firstCard(p1), 0, 1);
        settle();

        p2.close(); // dropped connection, no Leave
        clients.remove(p2);
        long deadline = System.nanoTime() + STEP_DEADLINE_MS * 1_000_000L;
        while (server.isConnected(PieceAlignment.P2)) {
            server.tick(TICK);
            sleepBriefly();
            assertTrue(System.nanoTime() - deadline < 0, "the server noticed the disconnect");
        }

        join(SeatRequest.P2, new ArrayList<>());
        settle();
        assertTrue(server.isConnected(PieceAlignment.P2));
    }
}
