package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.net.transport.LineLink;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LobbyConnectionTest {
    private static final LobbyEndpoint ENDPOINT = new LobbyEndpoint("example.com", 443, true);

    private static void pollUntil(LobbyConnection connection, LobbyConnection.Phase phase) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (connection.getPhase() != phase) {
            connection.poll();
            assertTrue(System.nanoTime() - deadline < 0, "stuck in " + connection.getPhase());
            Thread.onSpinWait();
        }
    }

    @Test
    void aSuccessfulConnectBecomesReadyAndExposesItsClient() {
        InProcessLobbyTransport transport = new InProcessLobbyTransport();
        LobbyConnection connection = new LobbyConnection((endpoint, name) -> new LobbyClient(transport.connect(), name));
        connection.setPlayerName("ann");
        assertNull(connection.getClient());

        connection.connect(ENDPOINT);
        assertEquals(LobbyConnection.Phase.CONNECTING, connection.getPhase());
        pollUntil(connection, LobbyConnection.Phase.READY);
        assertNotNull(connection.getClient());
        connection.close();
        assertEquals(LobbyConnection.Phase.IDLE, connection.getPhase());
        assertNull(connection.getClient());
    }

    @Test
    void aFailedConnectBecomesFailedWithAMessageForThePlayer() {
        LobbyConnection connection = new LobbyConnection((endpoint, name) -> {
            throw new IOException("refused");
        });
        connection.connect(ENDPOINT);
        pollUntil(connection, LobbyConnection.Phase.FAILED);
        assertTrue(connection.getFailure().contains("example.com"), connection.getFailure());
        assertNull(connection.getClient());
    }

    @Test
    void aConnectThatFinishesAfterCloseIsDiscardedAndClosed() throws Exception {
        CountDownLatch dialing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        InProcessLobbyTransport transport = new InProcessLobbyTransport();
        LineLink[] link = new LineLink[1];
        LobbyConnection connection = new LobbyConnection((endpoint, name) -> {
            dialing.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            link[0] = transport.connect();
            return new LobbyClient(link[0], name);
        });
        connection.connect(ENDPOINT);
        assertTrue(dialing.await(10, TimeUnit.SECONDS));
        connection.close();
        release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (link[0] == null || link[0].isOpen()) {
            assertTrue(System.nanoTime() - deadline < 0, "the late client was never closed");
            Thread.onSpinWait();
        }
        connection.poll();
        assertEquals(LobbyConnection.Phase.IDLE, connection.getPhase());
    }

    @Test
    void aDroppedServerConnectionBecomesFailed() {
        InProcessLobbyTransport transport = new InProcessLobbyTransport();
        LineLink[] mine = new LineLink[1];
        LobbyConnection connection = new LobbyConnection((endpoint, name) -> {
            mine[0] = transport.connect();
            return new LobbyClient(mine[0], name);
        });
        connection.connect(ENDPOINT);
        pollUntil(connection, LobbyConnection.Phase.READY);
        mine[0].close();
        connection.poll();
        assertEquals(LobbyConnection.Phase.FAILED, connection.getPhase());
        assertTrue(connection.getFailure().contains("Disconnected"));
    }

    @Test
    void releaseLeavesTheClientOpenForTheMatchToTakeOver() {
        InProcessLobbyTransport transport = new InProcessLobbyTransport();
        LineLink[] mine = new LineLink[1];
        LobbyConnection connection = new LobbyConnection((endpoint, name) -> {
            mine[0] = transport.connect();
            return new LobbyClient(mine[0], name);
        });
        connection.connect(ENDPOINT);
        pollUntil(connection, LobbyConnection.Phase.READY);
        connection.release();
        assertEquals(LobbyConnection.Phase.IDLE, connection.getPhase());
        assertTrue(mine[0].isOpen());
    }

    @Test
    void connectingAgainSupersedesTheEarlierAttempt() {
        InProcessLobbyTransport transport = new InProcessLobbyTransport();
        LobbyConnection connection = new LobbyConnection((endpoint, name) -> new LobbyClient(transport.connect(), name));
        connection.connect(ENDPOINT);
        connection.connect(ENDPOINT);
        pollUntil(connection, LobbyConnection.Phase.READY);
        assertNotNull(connection.getClient());
        connection.close();
    }
}
