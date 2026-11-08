package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.client.ClientMatchSession;
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CLI-004: the render/read/dispatch loop, driven by a scripted {@link LineSource} against a real
 * in-process {@link MatchServer} (in-code content, no assets needed — mirrors
 * {@code CliJoinSessionTest}'s pattern from CLI-001). The server is ticked on its own thread
 * while {@link CliLoop#run} runs synchronously on the test thread, exactly like a real deployment
 * (a separate server process, ticking independently of the client's own poll loop).
 */
@Timeout(20)
class CliLoopTest {
    private MatchServer server;
    private Thread ticker;
    private final AtomicBoolean tickerRunning = new AtomicBoolean();

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        PieceRegistry.register(new PieceDefinition("Runner", 1, 3, 1, 2, 1, List.of()));
        CardCatalog.registerSummon("Runner", "Runner");
    }

    @AfterEach
    void tearDown() {
        stopTicker();
        if (server != null) server.close();
    }

    @Test
    void scriptedRun_rendersStateAndRespondsToEveryCommandKind() throws Exception {
        List<String> deck = List.of("Runner", "Runner", "Runner", "Runner");
        MatchConfig config = MatchConfig.standard(GameMode.ONLINE_MATCH, deck, deck, 1L, Map.of(PieceAlignment.P2, BotKind.BASIC));
        TcpTransport transport = new TcpTransport("127.0.0.1", 0);
        server = new MatchServer(config, transport);
        startTicker();

        CliOptions options = CliOptions.parse(new String[]{
                "--mode", "join", "--host", "127.0.0.1", "--port", String.valueOf(transport.getPort()),
                "--seat", "p1", "--name", "cli-test"});

        try (ClientMatchSession session = options.startSession()) {
            awaitPlaying(session);

            List<String> script = List.of(
                    "board",
                    "legal",
                    "show nonexistent-piece",
                    "bogus command here",
                    "end",
                    "quit");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

            CliLoop.run(session, LineSource.script(script), out);

            String transcript = buffer.toString(StandardCharsets.UTF_8);
            assertTrue(transcript.contains("mana: P1="), "status line rendered: " + transcript);
            assertTrue(transcript.contains("your hand:"), "hand summary rendered: " + transcript);
            assertTrue(transcript.contains("seat P1"), "legal actions rendered for P1: " + transcript);
            assertTrue(transcript.contains("no such piece: nonexistent-piece"), "unknown piece reported: " + transcript);
            assertTrue(transcript.contains("error:"), "malformed command reported, not thrown: " + transcript);
            assertTrue(transcript.contains("quit"), "quit acknowledged: " + transcript);
            assertNull(session.viewState().getLastRejection(), "end turn was legal and accepted");
        }
    }

    @Test
    void scriptRunsOutOfLines_stopsCleanlyWithoutAnExplicitQuit() throws Exception {
        List<String> deck = List.of("Runner", "Runner", "Runner", "Runner");
        MatchConfig config = MatchConfig.standard(GameMode.ONLINE_MATCH, deck, deck, 1L, Map.of(PieceAlignment.P2, BotKind.BASIC));
        TcpTransport transport = new TcpTransport("127.0.0.1", 0);
        server = new MatchServer(config, transport);
        startTicker();

        CliOptions options = CliOptions.parse(new String[]{
                "--mode", "join", "--host", "127.0.0.1", "--port", String.valueOf(transport.getPort()), "--seat", "p1"});

        try (ClientMatchSession session = options.startSession()) {
            awaitPlaying(session);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

            CliLoop.run(session, LineSource.script(List.of("board")), out);

            assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("mana: P1="));
        }
    }

    private void startTicker() {
        tickerRunning.set(true);
        ticker = new Thread(() -> {
            while (tickerRunning.get()) {
                server.tick(0.05f);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "test-server-ticker");
        ticker.setDaemon(true);
        ticker.start();
    }

    private void stopTicker() {
        tickerRunning.set(false);
        if (ticker != null) {
            try {
                ticker.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void awaitPlaying(ClientMatchSession session) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (true) {
            session.poll();
            ClientMatchSession.Status status = session.getStatus();
            if (status.isReady()) return;
            if (status.isOver()) fail("session ended before PLAYING: " + status);
            if (System.nanoTime() - deadline > 0) fail("never reached PLAYING: " + status);
            Thread.sleep(5);
        }
    }
}
