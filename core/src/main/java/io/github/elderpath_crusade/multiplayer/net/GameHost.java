package io.github.elderpath_crusade.multiplayer.net;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.utils.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Listens for one guest connection (single 1v1 prototype, no lobby/matchmaking), executes
 * its NetworkCommands via GameServer exactly as a local click would, and relays every
 * GameEvent this process's TypedEventBus emits back to the guest. The host is the sole
 * simulation authority for the match.
 */
public class GameHost {
    private ServerSocket serverSocket;
    private volatile NetworkConnection connection;

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        registerEventRelay();
        Thread acceptThread = new Thread(() -> {
            try {
                Socket socket = serverSocket.accept();
                connection = new NetworkConnection(socket);
                Logger.log("GameHost", "Guest connected from " + socket.getRemoteSocketAddress());
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

    /** Poll every frame: applies any commands the guest sent since the last call. */
    public void update() {
        if (connection == null) return;
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
        bus.register(PieceDiedEvent.class, this::relay);
        bus.register(PieceKilledEvent.class, this::relay);
        bus.register(ManaChangedEvent.class, this::relay);
        bus.register(ActionsResetEvent.class, this::relay);
        bus.register(ActionSpentEvent.class, this::relay);
        bus.register(GameWonEvent.class, this::relay);
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
