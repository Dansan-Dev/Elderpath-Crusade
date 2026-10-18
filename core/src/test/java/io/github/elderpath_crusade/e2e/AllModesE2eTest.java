package io.github.elderpath_crusade.e2e;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.server.MatchFixtures;
import io.github.elderpath_crusade.server.ScriptedMatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every way a match can be played, end to end through MatchServer, the protocol and the client
 * replica — against every ServerBackend. MatchHarness checks the invariants after every tick
 * (replica == server view, no hidden-information leak, no rejected driver command, no stalled
 * bot pick); each scenario additionally checks that the match runs to a winner or the turn cap.
 *
 * Modes: solo (one client vs a server-side bot), hosted (two clients), joining-hosted (the
 * guest's side: joins a waiting match, drops and rejoins mid-match), online dedicated (two
 * clients on a server neither of them hosts, any connect order), and vs bots (a bot in either
 * seat, bot vs bot with no clients, a client-side "remote bot" vs a server-side bot).
 */
@Tag("e2e")
@Timeout(120)
class AllModesE2eTest {
    private static final int MAX_TURNS = 60;

    private MatchHarness harness;

    @BeforeEach
    void setUp() {
        MatchFixtures.setUpContext();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) harness.close();
        MatchFixtures.tearDownContext();
    }

    // ---------------------------------------------------------------- scenario sources

    static Stream<Arguments> backendsAndBots() {
        List<Arguments> out = new ArrayList<>();
        for (ServerBackend.Kind kind : ServerBackend.Kind.values()) {
            for (BotKind bot : BotKind.values()) out.add(Arguments.of(kind, bot));
        }
        return out.stream();
    }

    static Stream<Arguments> backendsAndSeeds() {
        List<Arguments> out = new ArrayList<>();
        for (ServerBackend.Kind kind : ServerBackend.Kind.values()) {
            for (long seed = 1; seed <= 3; seed++) out.add(Arguments.of(kind, seed));
        }
        return out.stream();
    }

    static Stream<Arguments> backendsBotsAndSeeds() {
        List<Arguments> out = new ArrayList<>();
        for (ServerBackend.Kind kind : ServerBackend.Kind.values()) {
            for (BotKind bot : BotKind.values()) {
                for (long seed = 1; seed <= 3; seed++) out.add(Arguments.of(kind, bot, seed));
            }
        }
        return out.stream();
    }

    // ---------------------------------------------------------------- solo

    @ParameterizedTest(name = "{0} vs {1}")
    @MethodSource("backendsAndBots")
    void solo_clientVsServerSideBot_playsToTheEnd(ServerBackend.Kind kind, BotKind bot) {
        harness = start(kind, config(3L, Map.of(PieceAlignment.P2, bot)));
        TestClient human = harness.join(SeatRequest.ANY, "solo", new RemoteBotDriver());

        Optional<PieceAlignment> winner = harness.runToEnd(MAX_TURNS);

        assertEquals(PieceAlignment.P1, human.state().getSeat());
        assertEquals("BOT_" + bot, human.state().getSlots().get(PieceAlignment.P2));
        assertTrue(harness.turns() >= 2, "the bot took its turns");
        assertTrue(human.state().getPieces().stream().anyMatch(p -> p.owner() == PieceAlignment.P2)
                || human.state().isEnded(), "the bot's pieces reach the client");
        assertOutcomeSeenByAll(winner, human);
    }

    // ---------------------------------------------------------------- hosted

    @ParameterizedTest(name = "{0}")
    @EnumSource(ServerBackend.Kind.class)
    void hosted_twoClients_scriptedMatchToAWin(ServerBackend.Kind kind) {
        harness = start(kind, MatchFixtures.config(MatchFixtures.deckOf("Zapper", 12), MatchFixtures.deckOf("Runner", 12), Map.of()));
        TestClient host = harness.join(SeatRequest.P1, "host", null);
        TestClient guest = harness.join(SeatRequest.P2, "guest", null);
        harness.expectRejections(true); // the script sends one illegal command on purpose

        new ScriptedMatch(host.client(), guest.client(), harness::step).play();

        assertTrue(harness.ended());
        assertTrue(guest.rawLines().stream().anyMatch(l -> l.contains("\"CardPlayedEvent\"") && l.contains("Zapper")),
                "playing a card is public");
    }

    @ParameterizedTest(name = "{0} seed {1}")
    @MethodSource("backendsAndSeeds")
    void hosted_twoClientDrivenPlayers_playToTheEnd(ServerBackend.Kind kind, long seed) {
        harness = start(kind, config(seed, Map.of()));
        TestClient host = harness.join(SeatRequest.P1, "host", new RemoteBotDriver());
        TestClient guest = harness.join(SeatRequest.P2, "guest", new RemoteBotDriver());

        Optional<PieceAlignment> winner = harness.runToEnd(MAX_TURNS);

        assertTrue(harness.turns() >= 2);
        assertOutcomeSeenByAll(winner, host, guest);
    }

    // ---------------------------------------------------------------- joining-hosted

    @ParameterizedTest(name = "{0}")
    @EnumSource(ServerBackend.Kind.class)
    void joiningHosted_guestJoinsAWaitingMatch_dropsAndRejoinsMidMatch(ServerBackend.Kind kind) {
        harness = start(kind, config(5L, Map.of()));
        TestClient host = harness.join(SeatRequest.P1, "host", new RemoteBotDriver());
        for (int i = 0; i < 3; i++) harness.step();
        assertTrue(host.state().isWelcomed());
        assertFalse(host.state().isStarted(), "a hosted match waits for its guest");

        TestClient guest = harness.join(SeatRequest.P2, "guest", new RemoteBotDriver());
        harness.step();
        assertTrue(guest.state().isStarted(), "the guest's snapshot shows the started match");
        assertEquals(PieceAlignment.P2, guest.state().getSeat());

        assertTrue(harness.runToEnd(6).isEmpty() || harness.ended(), "a few turns in");
        if (!harness.ended()) {
            harness.leave(guest);
            harness.step();
            TestClient back = harness.rejoin(SeatRequest.P2, "guest", new RemoteBotDriver()); // replica checked against the server right here
            assertEquals(PieceAlignment.P2, back.state().getSeat());
            assertFalse(back.state().getPieces().isEmpty(), "the snapshot carries the board so far");
            assertOutcomeSeenByAll(harness.runToEnd(MAX_TURNS), host, back);
        }
    }

    // ---------------------------------------------------------------- online dedicated

    @ParameterizedTest(name = "{0}")
    @EnumSource(ServerBackend.Kind.class)
    void onlineDedicated_twoClientsAskingForAnySeat_getOneEach(ServerBackend.Kind kind) {
        harness = start(kind, config(2L, Map.of()));
        TestClient first = harness.join(SeatRequest.ANY, "first", new RemoteBotDriver());
        TestClient second = harness.join(SeatRequest.ANY, "second", new RemoteBotDriver());

        Optional<PieceAlignment> winner = harness.runToEnd(MAX_TURNS);

        assertEquals(PieceAlignment.P1, first.state().getSeat());
        assertEquals(PieceAlignment.P2, second.state().getSeat());
        assertOutcomeSeenByAll(winner, first, second);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ServerBackend.Kind.class)
    void onlineDedicated_p2ConnectsFirst_anyGetsTheRemainingSeat(ServerBackend.Kind kind) {
        harness = start(kind, config(4L, Map.of()));
        TestClient p2 = harness.join(SeatRequest.P2, "p2-first", new RemoteBotDriver());
        harness.step();
        assertFalse(p2.state().isStarted());
        TestClient any = harness.join(SeatRequest.ANY, "any-second", new RemoteBotDriver());

        Optional<PieceAlignment> winner = harness.runToEnd(MAX_TURNS);

        assertEquals(PieceAlignment.P2, p2.state().getSeat());
        assertEquals(PieceAlignment.P1, any.state().getSeat());
        assertOutcomeSeenByAll(winner, p2, any);
    }

    // ---------------------------------------------------------------- vs bots

    @ParameterizedTest(name = "{0} vs {1}")
    @MethodSource("backendsAndBots")
    void vsBots_botInTheFirstSeat_clientPlaysSecond(ServerBackend.Kind kind, BotKind bot) {
        harness = start(kind, config(6L, Map.of(PieceAlignment.P1, bot)));
        TestClient human = harness.join(SeatRequest.ANY, "second", new RemoteBotDriver());

        Optional<PieceAlignment> winner = harness.runToEnd(MAX_TURNS);

        assertEquals(PieceAlignment.P2, human.state().getSeat(), "the only human seat");
        assertTrue(harness.turns() >= 2);
        assertOutcomeSeenByAll(winner, human);
    }

    @ParameterizedTest(name = "{0} {1} seed {2}")
    @MethodSource("backendsBotsAndSeeds")
    void vsBots_botVsBot_withNoClients_terminates(ServerBackend.Kind kind, BotKind bot, long seed) {
        // Identical SmartBots mirror each other into a stalemate on the symmetric standard map (BUG-029),
        // so their "race to a win" runs on the lopsided map; BasicBots only have to keep the match moving.
        Map<PieceAlignment, BotKind> bots = Map.of(PieceAlignment.P1, bot, PieceAlignment.P2, bot);
        harness = start(kind, bot == BotKind.SMART
                ? MatchFixtures.lopsidedConfig(mixedDeck(), mixedDeck(), seed, bots)
                : config(seed, bots));

        Optional<PieceAlignment> winner = harness.runToEnd(MAX_TURNS);

        assertTrue(harness.turns() >= 2, "both bots took turns");
        if (bot == BotKind.SMART) assertTrue(winner.isPresent(), "SmartBots race to a win");
    }

    // ---------------------------------------------------------------- helpers

    private static MatchHarness start(ServerBackend.Kind kind, MatchConfig config) {
        MatchHarness h = new MatchHarness(kind.create());
        h.start(config);
        return h;
    }

    /** The standard board, both sides with a mixed deck (plain units, a PIECE-pick ability, a TILE-pick ability). */
    private static MatchConfig config(long seed, Map<PieceAlignment, BotKind> bots) {
        return MatchConfig.standard(GameMode.ONLINE_MATCH, mixedDeck(), mixedDeck(), seed, bots);
    }

    private static List<String> mixedDeck() {
        List<String> deck = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            deck.add("Runner");
            deck.add("Zapper");
            deck.add("Conjurer");
        }
        return deck;
    }

    /** If the match ended, every client got MatchEnded with the same winner. */
    private static void assertOutcomeSeenByAll(Optional<PieceAlignment> winner, TestClient... clients) {
        if (winner.isEmpty()) return; // reached the turn cap — still a clean termination
        for (TestClient c : clients) {
            assertTrue(c.state().isEnded(), c + " got MatchEnded");
            assertEquals(winner.get(), c.state().getWinner(), c + " agrees on the winner");
        }
    }
}
