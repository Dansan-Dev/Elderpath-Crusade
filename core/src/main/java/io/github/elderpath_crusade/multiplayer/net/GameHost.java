package io.github.elderpath_crusade.multiplayer.net;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.systems.PlayerSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.utils.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Listens for one guest connection (single 1v1 prototype, no lobby/matchmaking), executes
 * its NetworkCommands via GameServer exactly as a local click would, and relays every
 * GameEvent this process's TypedEventBus emits back to the guest. The host is the sole
 * simulation authority for the match.
 */
public class GameHost {
    private ServerSocket serverSocket;
    private volatile NetworkConnection connection;
    // Set on the background accept thread, consumed on the main thread via update() — building
    // the snapshot means reading live ECS/board state, which (like everything else touching
    // GameContext) must only ever happen on the main thread, same reasoning as
    // NetworkConnection's reader-thread-to-queue design.
    private volatile boolean snapshotPending = false;

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        registerEventRelay();
        Thread acceptThread = new Thread(() -> {
            try {
                Socket socket = serverSocket.accept();
                connection = new NetworkConnection(socket);
                Logger.log("GameHost", "Guest connected from " + socket.getRemoteSocketAddress());
                snapshotPending = true;
            } catch (IOException e) {
                if (!serverSocket.isClosed()) Logger.error("GameHost", "Accept failed: " + e.getMessage());
            }
        }, "game-host-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getPort() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    public boolean hasGuest() {
        return connection != null && connection.isConnected();
    }

    /** Poll every frame: sends the catch-up snapshot once a guest just connected, then applies any commands it sent since the last call. */
    public void update() {
        if (connection == null) return;
        if (snapshotPending) {
            snapshotPending = false;
            connection.send(GameSnapshotCodec.encode(buildSnapshot()));
        }
        String line;
        while ((line = connection.pollLine()) != null) {
            try {
                dispatch(NetworkCommandCodec.decode(line));
            } catch (Exception e) {
                Logger.error("GameHost", "Bad command from guest: " + e.getMessage());
            }
        }
    }

    private void dispatch(NetworkCommand command) {
        var server = GameContext.get().getGameServer();
        if (command instanceof NetworkCommand.MovePlot c) {
            server.movePlot(c.srcRow(), c.srcCol(), c.dstRow(), c.dstCol());
        } else if (command instanceof NetworkCommand.PlaySummonCard c) {
            server.playSummonCard(c.alignment(), c.handIndex(), c.row(), c.col());
        } else if (command instanceof NetworkCommand.PlaySpellCard c) {
            server.playSpellCard(c.alignment(), c.handIndex(), c.targetRow(), c.targetCol());
        } else if (command instanceof NetworkCommand.EndTurn c) {
            if (c.alignment() == GameContext.get().getTurnManager().getCurrentPlayer()) {
                GameContext.get().getTurnManager().endTurn();
            }
        }
    }

    private void registerEventRelay() {
        TypedEventBus bus = TypedEventBus.get();
        bus.register(TurnStartedEvent.class, this::relay);
        bus.register(TurnEndedEvent.class, this::relay);
        bus.register(CardDrawnEvent.class, this::relay);
        bus.register(CardShuffledEvent.class, this::relay);
        bus.register(CardDiscardedEvent.class, this::relay);
        bus.register(CardPlayedEvent.class, this::relay);
        bus.register(PieceSpawnedEvent.class, this::relay);
        bus.register(PieceMovedEvent.class, this::relay);
        bus.register(PieceAttackedEvent.class, this::relay);
        bus.register(PieceDamagedEvent.class, this::relay);
        bus.register(PieceHealedEvent.class, this::relay);
        bus.register(PieceDiedEvent.class, this::relay);
        bus.register(PieceKilledEvent.class, this::relay);
        bus.register(ManaChangedEvent.class, this::relay);
        bus.register(ActionsResetEvent.class, this::relay);
        bus.register(ActionSpentEvent.class, this::relay);
        bus.register(GameWonEvent.class, this::relay);
    }

    private GameSnapshot buildSnapshot() {
        List<GameSnapshot.PieceState> pieces = new ArrayList<>();
        Board board = GameContext.get().getActiveBoard();
        if (board != null) {
            ImmutableArray<Entity> entities = GameContext.get().getEcsEngine().getEntitiesFor(
                    Family.all(IdentityComponent.class, PositionComponent.class,
                            AlignmentComponent.class, StatsComponent.class).get());
            for (int i = 0; i < entities.size(); i++) {
                Entity e = entities.get(i);
                IdentityComponent id = e.getComponent(IdentityComponent.class);
                PositionComponent pos = e.getComponent(PositionComponent.class);
                AlignmentComponent align = e.getComponent(AlignmentComponent.class);
                StatsComponent stats = e.getComponent(StatsComponent.class);
                pieces.add(new GameSnapshot.PieceState(id.id, align.alignment, id.name,
                        pos.row, pos.col, stats.currentHealth, stats.remainingActions));
            }
        }

        PlayerSystem playerSystem = GameContext.get().getEcsEngine().getSystem(PlayerSystem.class);
        return new GameSnapshot(
                pieces,
                handNames(PieceAlignment.P1),
                handNames(PieceAlignment.P2),
                playerSystem.getMana(PieceAlignment.P1),
                playerSystem.getMana(PieceAlignment.P2),
                GameContext.get().getTurnManager().getCurrentPlayer()
        );
    }

    private List<String> handNames(PieceAlignment alignment) {
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        Hand hand = playerState == null ? null : playerState.hand;
        if (hand == null) return List.of();
        List<String> names = new ArrayList<>();
        for (Card c : hand.getCards()) names.add(c.getDisplayName());
        return names;
    }

    private void relay(GameEvent event) {
        if (connection != null && connection.isConnected()) {
            connection.send(GameEventCodec.encode(event));
        }
    }

    public void stop() {
        if (connection != null) connection.close();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
    }
}
