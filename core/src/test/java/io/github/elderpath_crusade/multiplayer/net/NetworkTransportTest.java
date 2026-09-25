package io.github.elderpath_crusade.multiplayer.net;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Proves the network plumbing round-trips correctly over real loopback sockets:
 * a NetworkCommand sent by a GameClient reaches the host and dispatches through
 * GameServer, and a GameEvent emitted on the host's TypedEventBus is relayed to and
 * decoded by the client. Does not exercise full gameplay — see GameServer's own
 * behavior, which is covered by the existing card/board tests.
 */
class NetworkTransportTest {

    private GameHost host;
    private GameClient client;
    private Board board;

    @BeforeEach
    void setUp() throws Exception {
        TypedEventBus.get().clear();
        GameContext.create();
        board = mock(Board.class);
        GameContext.get().setActiveBoard(board);

        host = new GameHost();
        host.start(0); // ephemeral port, OS-assigned
        client = new GameClient("localhost", host.getPort());
        awaitTrue(() -> host.hasGuest() && client.isConnected(), "connection established");
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (host != null) host.stop();
    }

    @Test
    void commandFromClientDispatchesThroughGameServerOnHost() throws InterruptedException {
        AtomicInteger moveCalls = new AtomicInteger();
        when(board.movePiece(1, 2, 3, 4)).thenAnswer(inv -> {
            moveCalls.incrementAndGet();
            return true;
        });

        client.send(new NetworkCommand.MovePlot(1, 2, 3, 4));

        awaitTrue(() -> {
            host.update();
            return moveCalls.get() == 1;
        }, "command dispatched to GameServer/Board.movePiece");
    }

    @Test
    void connectingTriggersSnapshotDeliveryToClient() throws InterruptedException {
        List<GameSnapshot> received = new ArrayList<>();
        client.addSnapshotListener(received::add);

        awaitTrue(() -> {
            host.update(); // builds the snapshot on the main thread and sends it once, on connect
            client.update();
            return !received.isEmpty();
        }, "snapshot delivered to client on connect");

        assertEquals(PieceAlignment.P1, received.get(0).currentPlayer());
        assertTrue(received.get(0).pieces().isEmpty()); // no entities exist in this test's board
    }

    @Test
    void eventFromHostIsRelayedToAndDecodedByClient() throws InterruptedException {
        List<PieceMovedEvent> received = new ArrayList<>();
        client.addListener(event -> {
            if (event instanceof PieceMovedEvent moved) received.add(moved);
        });

        PieceMovedEvent event = new PieceMovedEvent(
                "piece-1", PieceAlignment.P1, 0, 0, 1, 0,
                PieceMovedEvent.MovementType.ACTIVE, "test");
        TypedEventBus.get().emit(event);

        awaitTrue(() -> {
            client.update();
            return received.contains(event);
        }, "event relayed to and decoded by client");
    }

    private void awaitTrue(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for: " + description);
    }
}
