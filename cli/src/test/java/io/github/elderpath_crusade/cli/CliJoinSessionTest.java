package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.net.transport.TcpTransport;
import io.github.elderpath_crusade.server.MatchServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CLI-001's core claim ("a session reaches Phase.PLAYING headlessly") proven for the one session
 * mode that doesn't spawn a child process: join. Content is registered in code, so this needs no
 * real asset files and runs in the cloud profile, unlike CLI-002's solo/hotseat/host equivalent
 * (CliSoloSessionTest, @RequiresAssets — those launch a real ServerLauncher process, which can't
 * see this JVM's in-code registrations).
 */
@Timeout(20)
class CliJoinSessionTest {
    private MatchServer server;
    private TcpTransport transport;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        PieceRegistry.register(new PieceDefinition("Runner", 1, 3, 1, 2, 1, List.of()));
        CardCatalog.registerSummon("Runner", "Runner");
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
    }

    @Test
    void join_connectsToARealServerAndReachesPlaying() throws Exception {
        List<String> deck = List.of("Runner", "Runner", "Runner", "Runner");
        // A bot in P2's slot means the match begins as soon as P1 (this test's join) is seated —
        // no second client needed to prove the join path itself reaches PLAYING.
        MatchConfig config = MatchConfig.standard(GameMode.ONLINE_MATCH, deck, deck, 1L, Map.of(PieceAlignment.P2, BotKind.BASIC));
        transport = new TcpTransport("127.0.0.1", 0);
        server = new MatchServer(config, transport);

        CliOptions options = CliOptions.parse(new String[]{
                "--mode", "join", "--host", "127.0.0.1", "--port", String.valueOf(transport.getPort()),
                "--seat", "p1", "--name", "cli-test"});

        try (ClientMatchSession session = options.startSession()) {
            ClientMatchSession.Status status = pumpUntilReadyOrOver(session);
            assertTrue(status.isReady(), "expected PLAYING, got " + status);
            assertEquals(PieceAlignment.P1, session.getControlledSeats().get(0));
            ClientMatchState state = session.viewState();
            assertNotNull(state);
            assertTrue(state.isStarted());
            assertNull(state.getLastError());
        }
    }

    @Test
    void join_withNoServerListening_eventuallyReportsFailure() throws Exception {
        CliOptions options = CliOptions.parse(new String[]{
                "--mode", "join", "--host", "127.0.0.1", "--port", "1", "--seat", "any"});

        try (ClientMatchSession session = options.startSession()) {
            ClientMatchSession.Status status = pumpUntilReadyOrOver(session);
            assertTrue(status.isOver(), "expected a terminal status, got " + status);
        }
    }

    private ClientMatchSession.Status pumpUntilReadyOrOver(ClientMatchSession session) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (true) {
            if (server != null) server.tick(0.1f);
            session.poll();
            ClientMatchSession.Status status = session.getStatus();
            if (status.isReady() || status.isOver()) return status;
            if (System.nanoTime() - deadline > 0) fail("never reached PLAYING or a terminal status: " + status);
            Thread.sleep(5);
        }
    }
}
