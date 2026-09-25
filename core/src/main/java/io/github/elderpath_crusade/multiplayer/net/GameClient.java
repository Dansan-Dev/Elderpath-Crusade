package io.github.elderpath_crusade.multiplayer.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.utils.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Connects to a GameHost. Sends this side's requested actions as NetworkCommands and
 * delivers relayed GameEvents (and the one-time GameSnapshot, for a late join) to registered
 * listeners. Runs no gameplay ECS logic itself — the host is the sole simulation authority;
 * a listener here must only reflect state (rendering), never re-simulate ability/combat
 * resolution.
 */
public class GameClient {
    private static final ObjectMapper PEEK_MAPPER = new ObjectMapper();

    private final NetworkConnection connection;
    private final List<Consumer<GameEvent>> listeners = new ArrayList<>();
    private final List<Consumer<GameSnapshot>> snapshotListeners = new ArrayList<>();

    public GameClient(String hostAddress, int port) throws IOException {
        this.connection = new NetworkConnection(new Socket(hostAddress, port));
    }

    public void addListener(Consumer<GameEvent> listener) {
        listeners.add(listener);
    }

    public void addSnapshotListener(Consumer<GameSnapshot> listener) {
        snapshotListeners.add(listener);
    }

    public void send(NetworkCommand command) {
        connection.send(NetworkCommandCodec.encode(command));
    }

    /** Poll every frame: delivers any GameEvents/GameSnapshot the host sent since the last call. */
    public void update() {
        String line;
        while ((line = connection.pollLine()) != null) {
            try {
                JsonNode node = PEEK_MAPPER.readTree(line);
                if (GameSnapshotCodec.TYPE.equals(node.get("type").asText())) {
                    GameSnapshot snapshot = GameSnapshotCodec.decode(line);
                    for (Consumer<GameSnapshot> listener : snapshotListeners) {
                        listener.accept(snapshot);
                    }
                } else {
                    GameEvent event = GameEventCodec.decode(line);
                    for (Consumer<GameEvent> listener : listeners) {
                        listener.accept(event);
                    }
                }
            } catch (Exception e) {
                Logger.error("GameClient", "Bad message from host: " + e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        return connection.isConnected();
    }

    public void close() {
        connection.close();
    }
}
