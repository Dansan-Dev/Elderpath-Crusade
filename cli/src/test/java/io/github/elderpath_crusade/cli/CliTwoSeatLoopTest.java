package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.GameContext;
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
 * CLI-005: a networked match played by two independent CLI sessions — each with its own
 * {@link ClientMatchSession} and its own {@link CliLoop} run — exactly like two separate CLI
 * processes would (each driving its own script, unaware of the other, talking only to the shared
 * server). Reuses CliLoopTest's in-process/in-code pattern (a real MatchServer, no real assets
 * needed) rather than spawning real child processes.
 *
 * True hotseat (one session, two connections, from {@code --mode hotseat}) and a real
 * {@code --mode host} launching an actual child server are covered by {@code CliHotseatAndHostTest}
 * — like CliSoloSessionTest, those need a real ServerLauncher process and so real asset files,
 * @RequiresAssets-gated and unverifiable in this sandbox.
 */
@Timeout(20)
class CliTwoSeatLoopTest {
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
    void networkedMatch_twoSeparateCliSessionsPlayAgainstEachOther() throws Exception {
        List<String> deck = List.of("Runner", "Runner", "Runner", "Runner");
        MatchConfig config = MatchConfig.standard(GameMode.ONLINE_MATCH, deck, deck, 1L, Map.of());
        TcpTransport transport = new TcpTransport("127.0.0.1", 0);
        server = new MatchServer(config, transport);
        startTicker();
        int port = transport.getPort();

        CliOptions p1Options = CliOptions.parse(new String[]{
                "--mode", "join", "--host", "127.0.0.1", "--port", String.valueOf(port), "--seat", "p1", "--name", "p1-cli"});
        CliOptions p2Options = CliOptions.parse(new String[]{
                "--mode", "join", "--host", "127.0.0.1", "--port", String.valueOf(port), "--seat", "p2", "--name", "p2-cli"});

        try (ClientMatchSession p1Session = p1Options.startSession();
             ClientMatchSession p2Session = p2Options.startSession()) {
            // Both sessions' connections start concurrently above, but the match itself only
            // begins once BOTH seats are taken — awaiting p1Session alone first would starve
            // p2Session of the polling it needs to even finish connecting, let alone get seated.
            awaitBothPlaying(p1Session, p2Session);

            ByteArrayOutputStream p1Buffer = new ByteArrayOutputStream();
            ByteArrayOutputStream p2Buffer = new ByteArrayOutputStream();
            PrintStream p1Out = new PrintStream(p1Buffer, true, StandardCharsets.UTF_8);
            PrintStream p2Out = new PrintStream(p2Buffer, true, StandardCharsets.UTF_8);

            // Two independent "CLI processes" each with their own session and loop — proven
            // sequentially, with an explicit sync point between phases, rather than racing both
            // loops concurrently from the start: a script's lines are read as fast as they can be
            // (no pacing built in — that's a human's or a scripted scenario's own job), so without
            // this, P2's "end" + "quit" could blow straight through before P1's own "end" has
            // even reached the server, since nothing paces one session's script against the
            // other's. Neither session drives or waits on the other directly — only on the
            // server's state, exactly like two real separate CLI processes would.
            CliLoop.run(p1Session, LineSource.script(List.of("end", "quit")), p1Out);
            awaitCurrentPlayer(p2Session, PieceAlignment.P2);
            CliLoop.run(p2Session, LineSource.script(List.of("end", "quit")), p2Out);

            String p1Transcript = p1Buffer.toString(StandardCharsets.UTF_8);
            String p2Transcript = p2Buffer.toString(StandardCharsets.UTF_8);
            assertTrue(p1Transcript.contains("your hand:"), "P1's own loop rendered its own state: " + p1Transcript);
            assertTrue(p2Transcript.contains("your hand:"), "P2's own loop rendered its own state: " + p2Transcript);
            // Each session only ever announces turns it itself controls — it doesn't know or
            // care what the other side's screen would show, same as two real CLI processes.
            assertTrue(p1Transcript.contains("=== P1's turn ==="), "P1's session announces its own turn: " + p1Transcript);
            assertFalse(p1Transcript.contains("=== P2's turn ==="), "P1's session never speaks for P2: " + p1Transcript);
            assertTrue(p2Transcript.contains("=== P2's turn ==="), "P2's session announces its own turn: " + p2Transcript);
            assertFalse(p2Transcript.contains("=== P1's turn ==="), "P2's session never speaks for P1: " + p2Transcript);
            assertTrue(p1Transcript.contains("quit") && p2Transcript.contains("quit"));
            assertNull(p1Session.viewState().getLastRejection());
            assertNull(p2Session.viewState().getLastRejection());
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

    private void awaitBothPlaying(ClientMatchSession a, ClientMatchSession b) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (true) {
            a.poll();
            b.poll();
            ClientMatchSession.Status statusA = a.getStatus();
            ClientMatchSession.Status statusB = b.getStatus();
            if (statusA.isReady() && statusB.isReady()) return;
            if (statusA.isOver()) fail("session a ended before PLAYING: " + statusA);
            if (statusB.isOver()) fail("session b ended before PLAYING: " + statusB);
            if (System.nanoTime() - deadline > 0) fail("never reached PLAYING: a=" + statusA + " b=" + statusB);
            Thread.sleep(5);
        }
    }

    private void awaitCurrentPlayer(ClientMatchSession session, PieceAlignment expected) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (true) {
            session.poll();
            if (session.currentPlayer() == expected) return;
            if (System.nanoTime() - deadline > 0) {
                fail("current player never became " + expected + " (was " + session.currentPlayer() + ")");
            }
            Thread.sleep(5);
        }
    }
}
