package io.github.elderpath_crusade.server.launcher;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.e2e.MatchHarness;
import io.github.elderpath_crusade.e2e.RemoteBotDriver;
import io.github.elderpath_crusade.e2e.TestClient;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The end-to-end modes of core's AllModesE2eTest, against the production path: a real
 * ServerLauncher child process over TCP, with the real assets. The harness checks after every
 * round that the clients' replicas agree with each other, that no client receives the
 * opponent's card ids/names, and that no client-driver command is rejected; each match must
 * run to a winner or the turn cap.
 */
@Tag("e2e")
@RequiresAssets
@Timeout(180)
class ChildProcessE2eTest {
    private static final int MAX_TURNS = 40;
    /** What client-driven seats play: the game's starting deck. */
    private static final List<String> STARTER = List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub");
    /** What bot seats play: a deck SmartBot reliably wins or loses with. */
    private static final List<String> BOT_DECK = List.of("Rogue", "Rogue", "Rifleman", "Rifleman");

    private MatchHarness harness;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        ChildProcessBackend.loadRealDefinitions();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) harness.close();
        CardCatalog.clear();
        TypedEventBus.get().clear();
    }

    @ParameterizedTest(name = "vs {0}")
    @EnumSource(BotKind.class)
    void solo_clientVsServerSideBot(BotKind bot) {
        harness = start(config(STARTER, BOT_DECK, Map.of(PieceAlignment.P2, bot)));
        TestClient human = harness.join(SeatRequest.ANY, "solo", new RemoteBotDriver());

        Optional<PieceAlignment> winner = harness.runToEnd(MAX_TURNS);

        assertEquals(PieceAlignment.P1, human.state().getSeat());
        assertEquals("BOT_" + bot, human.state().getSlots().get(PieceAlignment.P2));
        assertTrue(harness.turns() >= 2, "the bot took its turns");
        assertOutcome(winner, human);
    }

    @Test
    void hosted_twoClientDrivenPlayers() {
        harness = start(config(STARTER, STARTER, Map.of()));
        TestClient host = harness.join(SeatRequest.P1, "host", new RemoteBotDriver());
        TestClient guest = harness.join(SeatRequest.P2, "guest", new RemoteBotDriver());

        assertOutcome(harness.runToEnd(MAX_TURNS), host, guest);
    }

    @Test
    void joiningHosted_guestJoinsAWaitingMatch_dropsAndRejoins() {
        harness = start(config(STARTER, STARTER, Map.of()));
        TestClient host = harness.join(SeatRequest.P1, "host", new RemoteBotDriver());
        harness.step();
        assertFalse(host.state().isStarted(), "a hosted match waits for its guest");

        TestClient guest = harness.join(SeatRequest.P2, "guest", new RemoteBotDriver());
        harness.step();
        assertTrue(guest.state().isStarted());

        harness.runToEnd(6);
        if (!harness.ended()) {
            harness.leave(guest);
            harness.step();
            TestClient back = harness.rejoin(SeatRequest.P2, "guest", new RemoteBotDriver());
            assertEquals(PieceAlignment.P2, back.state().getSeat(), "rejoined (error: " + back.state().getLastError()
                    + ", connected: " + back.client().isConnected() + ", server alive: " + harness.backend().ended() + ")");
            assertEquals(host.state().toView().pieces(), back.state().toView().pieces(), "the rejoin snapshot matches the host's board");
            assertOutcome(harness.runToEnd(MAX_TURNS), host, back);
        }
    }

    @Test
    void onlineDedicated_twoClientsAskingForAnySeat() {
        harness = start(config(STARTER, STARTER, Map.of()));
        TestClient first = harness.join(SeatRequest.ANY, "first", new RemoteBotDriver());
        harness.step(); // seat the first before the second asks, so the order is deterministic
        TestClient second = harness.join(SeatRequest.ANY, "second", new RemoteBotDriver());

        Optional<PieceAlignment> winner = harness.runToEnd(MAX_TURNS);

        assertEquals(PieceAlignment.P1, first.state().getSeat());
        assertEquals(PieceAlignment.P2, second.state().getSeat());
        assertOutcome(winner, first, second);
    }

    @Test
    void onlineDedicated_p2ConnectsFirst_anyGetsTheRemainingSeat() {
        harness = start(config(STARTER, STARTER, Map.of()));
        TestClient p2 = harness.join(SeatRequest.P2, "p2-first", new RemoteBotDriver());
        harness.step();
        assertFalse(p2.state().isStarted(), "the match waits for its second seat");
        TestClient any = harness.join(SeatRequest.ANY, "any-second", new RemoteBotDriver());

        Optional<PieceAlignment> winner = harness.runToEnd(MAX_TURNS);

        assertEquals(PieceAlignment.P2, p2.state().getSeat());
        assertEquals(PieceAlignment.P1, any.state().getSeat());
        assertOutcome(winner, p2, any);
    }

    @ParameterizedTest(name = "{0} first")
    @EnumSource(BotKind.class)
    void vsBots_botInTheFirstSeat(BotKind bot) {
        harness = start(config(BOT_DECK, STARTER, Map.of(PieceAlignment.P1, bot)));
        TestClient human = harness.join(SeatRequest.ANY, "second", new RemoteBotDriver());

        Optional<PieceAlignment> winner = harness.runToEnd(MAX_TURNS);

        assertEquals(PieceAlignment.P2, human.state().getSeat());
        assertOutcome(winner, human);
    }

    @Test
    void vsBots_smartVsSmart_withNoClients_reportsAWinner() {
        harness = start(config(BOT_DECK, BOT_DECK, Map.of(PieceAlignment.P1, BotKind.SMART, PieceAlignment.P2, BotKind.SMART)));

        Optional<PieceAlignment> winner = harness.runToEnd(MAX_TURNS);

        assertTrue(winner.isPresent(), "the server reported a winner");
    }

    private static MatchHarness start(MatchConfig config) {
        MatchHarness h = new MatchHarness(new ChildProcessBackend());
        h.start(config);
        return h;
    }

    private static MatchConfig config(List<String> p1Deck, List<String> p2Deck, Map<PieceAlignment, BotKind> bots) {
        return MatchConfig.standard(GameMode.ONLINE_MATCH, p1Deck, p2Deck, 1L, bots);
    }

    /** If the match ended, every client saw MatchEnded with the same winner. */
    private static void assertOutcome(Optional<PieceAlignment> winner, TestClient... clients) {
        if (winner.isEmpty()) return; // the turn cap — still a clean termination
        for (TestClient c : clients) {
            assertTrue(c.state().isEnded(), c + " got MatchEnded");
            assertEquals(winner.get(), c.state().getWinner(), c + " agrees on the winner");
        }
    }
}
