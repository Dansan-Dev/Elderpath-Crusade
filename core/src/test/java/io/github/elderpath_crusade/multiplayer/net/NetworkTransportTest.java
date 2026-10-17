package io.github.elderpath_crusade.multiplayer.net;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.match.ActionResult;
import io.github.elderpath_crusade.match.BoardGrid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

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
    private BoardGrid grid;

    @BeforeEach
    void setUp() throws Exception {
        TypedEventBus.get().clear();
        GameContext.create();
        GameContext.get().getTurnManager().reset(); // P1 to act
        grid = new BoardGrid(7, 5);
        GameContext.get().setActiveGrid(grid);

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
        GameContext.get().getTurnManager().setCurrentPlayerForReplica(PieceAlignment.P2); // the guest's turn
        Entity piece = spawn(PieceAlignment.P2, 5, 2);

        client.send(new NetworkCommand.MovePlot(5, 2, 4, 2));

        awaitTrue(() -> {
            host.update();
            return grid.getEntityAt(4, 2) == piece;
        }, "command dispatched through GameServer — the piece moved on the host's grid");
    }

    @Test
    void guestCommandsActAsTheGuestsSideRegardlessOfPayload() throws InterruptedException {
        List<ActionResult.Reason> rejections = new ArrayList<>();
        List<PieceAlignment> actors = new ArrayList<>();
        GameContext.get().getGameServer().setRejectionObserver((actor, result) -> {
            actors.add(actor);
            rejections.add(result.reason());
        });
        Entity hostPiece = spawn(PieceAlignment.P1, 1, 2);

        // P1's (the host's) turn: the guest can neither move the host's piece nor end its turn
        client.send(new NetworkCommand.MovePlot(1, 2, 2, 2));
        client.send(new NetworkCommand.EndTurn());
        awaitTrue(() -> {
            host.update();
            return rejections.size() == 2;
        }, "both guest commands rejected");
        assertEquals(List.of(ActionResult.Reason.NOT_YOUR_TURN, ActionResult.Reason.NOT_YOUR_TURN), rejections);
        assertEquals(List.of(PieceAlignment.P2, PieceAlignment.P2), actors, "the host acts as the connection's side");
        assertSame(hostPiece, grid.getEntityAt(1, 2));
        assertEquals(PieceAlignment.P1, GameContext.get().getTurnManager().getCurrentPlayer());

        // P2's turn: still can't move the host's piece
        GameContext.get().getTurnManager().setCurrentPlayerForReplica(PieceAlignment.P2);
        client.send(new NetworkCommand.MovePlot(1, 2, 2, 2));
        awaitTrue(() -> {
            host.update();
            return rejections.size() == 3;
        }, "move of the host's piece rejected");
        assertEquals(ActionResult.Reason.NOT_YOUR_PIECE, rejections.get(2));
        assertSame(hostPiece, grid.getEntityAt(1, 2));
    }

    private Entity spawn(PieceAlignment owner, int row, int col) {
        Entity piece = PieceFactory.createFromDefinition(GameContext.get().getEcsEngine(),
                new PieceDefinition("Wolf", 1, 3, 1, 1, 1, List.of()), owner,
                UUID.randomUUID().toString(), row, col);
        piece.getComponent(StatsComponent.class).remainingActions = 1;
        grid.addEntity(row, col, piece);
        return piece;
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
        assertTrue(received.get(0).pieces().isEmpty()); // no entities exist on this test's grid
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
