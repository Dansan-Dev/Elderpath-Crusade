package io.github.elderpath_crusade.server.launcher;

import io.github.elderpath_crusade.client.MatchClient;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.net.transport.TcpTransport;
import io.github.elderpath_crusade.server.ServerProcess;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real ServerLauncher as a child process (this test JVM's classpath carries the
 * server module) and talks to it over TCP — the production path for solo/hosted play.
 */
class ServerProcessTest {
    private static final String ASSETS = System.getProperty("elderpath.assetsDir", "../assets");
    private static final Duration DEADLINE = Duration.ofSeconds(20);

    private ServerProcess server;
    private MatchClient client;

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    @Test
    void badArguments_failToStartWithTheServersMessage() {
        IOException e = assertThrows(IOException.class, () -> ServerProcess.start(ServerProcess.Options.of("--p1", "god")));
        assertTrue(e.getMessage().contains("--p1 must be human, basic, smart, smart_aggressive or smart_defensive"), e.getMessage());
    }

    @Test
    void missingAssets_failToStart() {
        IOException e = assertThrows(IOException.class,
                () -> ServerProcess.start(ServerProcess.Options.of("--assets", "/nonexistent-elderpath-assets")));
        assertTrue(e.getMessage().contains("no data/pieces.yaml"), e.getMessage());
    }

    @Test
    @RequiresAssets
    void humanVsBot_clientIsSeatedAndTheBotTakesItsTurn() throws Exception {
        server = ServerProcess.start(ServerProcess.Options.of(
                "--assets", ASSETS, "--p2", "smart", "--seed", "1", "--time-scale", "20", "--tick-ms", "5"));
        assertTrue(server.getPort() > 0);

        client = new MatchClient(TcpTransport.connect("127.0.0.1", server.getPort(), null), SeatRequest.ANY, "tester");
        awaitClient(() -> client.getState().isStarted() && client.getState().getCurrentPlayer() == PieceAlignment.P1);
        assertEquals(PieceAlignment.P1, client.getState().getSeat());
        assertEquals("BOT_SMART", client.getState().getSlots().get(PieceAlignment.P2));
        assertFalse(client.getState().getOwnHand().isEmpty(), "P1 drew a starting hand");

        for (int turn = 0; turn < 2; turn++) {
            client.endTurn();
            awaitClient(() -> client.getState().getCurrentPlayer() == PieceAlignment.P2);
            awaitClient(() -> client.getState().getCurrentPlayer() == PieceAlignment.P1);
        }
        assertNull(client.getState().getLastRejection());
        assertTrue(server.isAlive());

        server.stop();
        assertEquals(0, server.awaitExit(Duration.ofSeconds(1)), "stdin close is a clean exit, not a kill");
    }

    @Test
    @RequiresAssets
    void noClient_serverExitsAfterIdleTimeout() throws Exception {
        server = ServerProcess.start(ServerProcess.Options.of("--assets", ASSETS, "--idle-timeout-s", "1"));
        assertEquals(0, server.awaitExit(DEADLINE));
    }

    @Test
    @RequiresAssets
    void botVsBot_reportsTheWinnerAndExits() throws Exception {
        server = ServerProcess.start(ServerProcess.Options.of(
                "--assets", ASSETS, "--p1", "smart", "--p2", "smart", "--seed", "1",
                "--p1-deck", "Rogue:2,Rifleman:2", "--p2-deck", "Rogue:2,Rifleman:2",
                "--time-scale", "40", "--tick-ms", "5"));
        String winner = server.awaitMatchEnded(DEADLINE);
        assertTrue("P1".equals(winner) || "P2".equals(winner), "winner reported: " + winner);
        assertEquals(0, server.awaitExit(DEADLINE));
    }

    @Test
    @RequiresAssets
    void aRunModifiedDeckCard_startsInsteadOfFailingAsUnknown() throws Exception {
        // BUG-044: a run-modified card's deck slot is a synthetic name (UPG-003, e.g.
        // "Wolf#run-0") that loadDefinitions used to reject outright — its own CardCatalog
        // entry only gets registered later, inside MatchSetup.prepare, from --p1-card-mods.
        // Reproduces exactly the crash a real roguelike-run battle hit once a deck card had
        // been modified by an Elite's card-mod reward (UPG-005): "unknown card in deck: Wolf#run-0".
        server = ServerProcess.start(ServerProcess.Options.of(
                "--assets", ASSETS, "--p2", "basic", "--seed", "1",
                "--p1-deck", "Wolf#run-0,Wolf Cub:3",
                "--p1-card-mods", "run-0@damage=2:"));
        assertTrue(server.getPort() > 0);

        client = new MatchClient(TcpTransport.connect("127.0.0.1", server.getPort(), null), SeatRequest.ANY, "tester");
        awaitClient(() -> client.getState().isStarted());
        assertNull(client.getState().getLastError());
    }

    @Test
    @RequiresAssets
    void healthPort_servesLivenessAndReadinessOverHttp() throws Exception {
        int healthPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            healthPort = probe.getLocalPort();
        }
        server = ServerProcess.start(ServerProcess.Options.of(
                "--assets", ASSETS, "--p2", "smart", "--seed", "1", "--health-port", String.valueOf(healthPort)));
        assertTrue(server.getPort() > 0);

        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> live = awaitHealthResponse(http, healthPort, "/healthz/live");
        assertEquals(200, live.statusCode());
        HttpResponse<String> ready = awaitHealthResponse(http, healthPort, "/healthz/ready");
        assertEquals(200, ready.statusCode());

        // Sprint 64: /diag rides the same health port, reporting real tick-timing stats once the
        // loop has actually ticked at least once (which `live` returning 200 above guarantees).
        HttpResponse<String> diag = awaitHealthResponse(http, healthPort, "/diag");
        assertEquals(200, diag.statusCode());
        assertTrue(diag.body().contains("\"tickCount\""), diag.body());
        assertFalse(diag.body().contains("\"tickCount\":0"), "the loop has ticked by now: " + diag.body());
    }

    /** The child process needs a moment to bind the health port after LISTENING is reported — poll briefly. */
    private HttpResponse<String> awaitHealthResponse(HttpClient http, int healthPort, String path) throws Exception {
        long deadline = System.nanoTime() + DEADLINE.toNanos();
        while (true) {
            try {
                return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + healthPort + path)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (System.nanoTime() - deadline > 0) throw e;
                Thread.sleep(20);
            }
        }
    }

    private void awaitClient(BooleanSupplier condition) throws InterruptedException {
        // Monotonic: this machine's wall clock jumps by tens of seconds (BUG-027 — a jump made this
        // deadline expire mid bot turn, and the teardown's server.stop() cut the bot's log short).
        long deadline = System.nanoTime() + DEADLINE.toNanos();
        while (true) {
            client.poll();
            if (condition.getAsBoolean()) return;
            assertNull(client.getState().getLastError(), "server error");
            if (System.nanoTime() - deadline > 0) fail("condition not reached; current player " + client.getState().getCurrentPlayer());
            Thread.sleep(5);
        }
    }
}
