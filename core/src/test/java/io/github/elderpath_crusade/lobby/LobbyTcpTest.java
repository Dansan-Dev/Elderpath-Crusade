package io.github.elderpath_crusade.lobby;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/** The lobby over real sockets: LobbyClient.connect → TcpLobbyTransport → LobbyServer. */
class LobbyTcpTest {

    private static void pump(LobbyServer server, LobbyClient client, java.util.function.BooleanSupplier done) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!done.getAsBoolean()) {
            server.tick();
            client.poll();
            assertTrue(System.nanoTime() - deadline < 0, "timed out");
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted");
            }
        }
    }

    @Test
    void aClientOverRealTcpCreatesAndListsALobby() throws IOException {
        TcpLobbyTransport transport = new TcpLobbyTransport("127.0.0.1", 0);
        LobbyServer server = new LobbyServer(transport, spec -> new CompletableFuture<>(), 1, card -> card.equals("Wolf"));
        try (LobbyClient client = LobbyClient.connect("127.0.0.1", transport.getPort(), false, "ann")) {
            pump(server, client, client::isWelcomed);
            client.createLobby("Real", "pw", List.of("Wolf"));
            pump(server, client, () -> client.getLobby() != null);
            assertTrue(client.isHost());
            client.requestList();
            pump(server, client, () -> !client.getLobbies().isEmpty());
            assertEquals("Real", client.getLobbies().get(0).name());
            assertTrue(client.getLobbies().get(0).passwordProtected());
        } finally {
            server.close();
        }
    }

    @Test
    void connectingToNothingFailsWithAnIOException() throws IOException {
        TcpLobbyTransport transport = new TcpLobbyTransport("127.0.0.1", 0);
        int port = transport.getPort();
        transport.close();
        assertThrows(IOException.class, () -> LobbyClient.connect("127.0.0.1", port, false, "ann"));
    }
}
