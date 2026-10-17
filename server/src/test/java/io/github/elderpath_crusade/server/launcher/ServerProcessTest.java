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
        assertTrue(e.getMessage().contains("--p1 must be human, basic or smart"), e.getMessage());
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

    private void awaitClient(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + DEADLINE.toMillis();
        while (true) {
            client.poll();
            if (condition.getAsBoolean()) return;
            assertNull(client.getState().getLastError(), "server error");
            if (System.currentTimeMillis() > deadline) fail("condition not reached; current player " + client.getState().getCurrentPlayer());
            Thread.sleep(5);
        }
    }
}
