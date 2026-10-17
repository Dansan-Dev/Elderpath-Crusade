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
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.match.PickResponder;
import io.github.elderpath_crusade.match.PickResponse;
import io.github.elderpath_crusade.match.PickRouter;
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
    /** The single guest connection always plays this side (see OnlineMatchSession.getLocalAlignment). */
    static final PieceAlignment GUEST_ALIGNMENT = PieceAlignment.P2;

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
        GameContext.get().getPickRouter().setRemoteResponder(GUEST_ALIGNMENT, relayPick);
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
        // The acting side comes from the connection (the guest is always GUEST_ALIGNMENT),
        // never from the command — GameServer validates everything else.
        var server = GameContext.get().getGameServer();
        if (command instanceof NetworkCommand.MovePlot c) {
            server.movePlot(GUEST_ALIGNMENT, c.srcRow(), c.srcCol(), c.dstRow(), c.dstCol());
        } else if (command instanceof NetworkCommand.PlaySummonCard c) {
            server.playSummonCard(GUEST_ALIGNMENT, c.instanceId(), c.row(), c.col());
        } else if (command instanceof NetworkCommand.PlaySpellCard c) {
            server.playSpellCard(GUEST_ALIGNMENT, c.instanceId(), c.targetRow(), c.targetCol());
        } else if (command instanceof NetworkCommand.EndTurn) {
            server.endTurn(GUEST_ALIGNMENT);
        } else if (command instanceof NetworkCommand.ActivateAbility c) {
            server.activateAbility(GUEST_ALIGNMENT, c.pieceId(), c.actionIndex());
        } else if (command instanceof NetworkCommand.AnswerPick c) {
            PickResponse response = c.cancelled() ? PickResponse.cancelled(c.pickId())
                    : PickResponse.chose(c.pickId(), new Coord(c.row(), c.col()));
            if (!server.answerPick(GUEST_ALIGNMENT, response).isOk()) {
                // Rejected: the guest's prompt is gone but the pick is still pending — ask again.
                PickRequest still = GameContext.get().getPickRouter().activeRequest(GUEST_ALIGNMENT);
                if (still != null && still.pickId() == c.pickId()) relayPick.onPickRequested(still, null);
            }
        }
    }

    /** Sends the guest's picks over the connection; with no guest connected, the pick is cancelled. */
    private final PickResponder relayPick = new PickResponder() {
        @Override
        public void onPickRequested(PickRequest request, PickRouter router) {
            if (connection != null && connection.isConnected()) {
                connection.send(PickMessageCodec.encodeRequested(request));
            } else {
                GameContext.get().getPickRouter().answer(request.owner(), PickResponse.cancelled(request.pickId()));
            }
        }

        @Override
        public void onPickCancelled(PickRequest request) {
            if (connection != null && connection.isConnected()) {
                connection.send(PickMessageCodec.encodeCancelled(request.pickId()));
            }
        }
    };

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
        if (GameContext.get().getActiveGrid() != null) {
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
                handOf(PieceAlignment.P1),
                handOf(PieceAlignment.P2),
                playerSystem.getMana(PieceAlignment.P1),
                playerSystem.getMana(PieceAlignment.P2),
                GameContext.get().getTurnManager().getCurrentPlayer()
        );
    }

    private List<CardInstance> handOf(PieceAlignment alignment) {
        return new ArrayList<>(GameContext.get().getPlayerManager().get(alignment).hand.getCards());
    }

    private void relay(GameEvent event) {
        if (connection != null && connection.isConnected()) {
            connection.send(GameEventCodec.encode(event));
        }
    }

    public void stop() {
        GameContext.get().getPickRouter().setRemoteResponder(GUEST_ALIGNMENT, null);
        if (connection != null) connection.close();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
    }
}
