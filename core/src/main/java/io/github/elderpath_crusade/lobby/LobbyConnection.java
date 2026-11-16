package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.utils.Logger;

import java.io.IOException;

/**
 * The game's one connection to a lobby service, shared by the online screens: connecting happens
 * on a background thread (a TCP/TLS connect can take seconds and must never freeze a frame), and
 * poll() — called every frame from the screen showing it — takes over the finished client and
 * drains it. A screen never touches sockets; it reads {@link #getPhase()} and {@link #getClient()}.
 */
public final class LobbyConnection implements AutoCloseable {
    public enum Phase { IDLE, CONNECTING, READY, FAILED }

    /** How a client gets made — the real one dials; tests supply their own. */
    @FunctionalInterface
    public interface Connector {
        LobbyClient connect(LobbyEndpoint endpoint, String playerName) throws IOException;
    }

    private final Connector connector;
    private LobbyClient client;
    private Phase phase = Phase.IDLE;
    private String failure = "";
    private String playerName = "Player";
    private String serverAddress = LobbyEndpoint.DEFAULT_ADDRESS;
    private int generation;
    private LobbyEndpoint endpoint;
    private RejoinTicket rejoinTicket;
    private LobbyClient arrived;
    private String arrivedFailure;

    public LobbyConnection() {
        this(dialingConnector());
    }

    /** The real connector: opens a TCP (or TLS) socket to the endpoint. */
    public static Connector dialingConnector() {
        return (endpoint, name) -> LobbyClient.connect(endpoint.host(), endpoint.port(), endpoint.tls(), name);
    }

    public LobbyConnection(Connector connector) {
        this.connector = connector;
    }

    public synchronized String getPlayerName() {
        return playerName;
    }

    public synchronized void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    /** What the player last typed as the server address — remembered across the online screens (this session only). */
    public synchronized String getServerAddress() {
        return serverAddress;
    }

    public synchronized void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    /** Closes any earlier connection and starts connecting to endpoint as the current player name. */
    public synchronized void connect(LobbyEndpoint endpoint) {
        closeClient();
        this.endpoint = endpoint;
        phase = Phase.CONNECTING;
        failure = "";
        int mine = ++generation;
        String name = playerName;
        Thread thread = new Thread(() -> dial(endpoint, name, mine), "lobby-connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void dial(LobbyEndpoint endpoint, String name, int mine) {
        LobbyClient made = null;
        String error = null;
        try {
            made = connector.connect(endpoint, name);
        } catch (IOException | RuntimeException e) {
            Logger.error("LobbyConnection", "Could not reach " + endpoint + ": " + e);
            error = "Could not reach the lobby server (" + endpoint.host() + ")";
        }
        synchronized (this) {
            if (mine != generation) {
                if (made != null) made.close(); // superseded or closed while dialling
                return;
            }
            arrived = made;
            arrivedFailure = error;
        }
    }

    /** Call every frame: takes over a freshly connected client and reads what the server sent. */
    public synchronized void poll() {
        if (phase == Phase.CONNECTING) {
            if (arrived != null) {
                client = arrived;
                arrived = null;
                phase = Phase.READY;
            } else if (arrivedFailure != null) {
                failure = arrivedFailure;
                arrivedFailure = null;
                phase = Phase.FAILED;
            }
        }
        if (phase == Phase.READY && client != null) {
            client.poll();
            // A client whose connection went to the match protocol is deliberately not ours any more.
            if (client.getMatchSeat() == null && !client.isConnected()) {
                failure = client.getLastError() != null ? client.getLastError().message() : "Disconnected from the lobby server";
                closeClient();
                phase = Phase.FAILED;
            }
        }
    }

    /** The endpoint of the latest connect(), or null. */
    public synchronized LobbyEndpoint getEndpoint() {
        return endpoint;
    }

    /** What the match screen needs to reconnect if the bridged connection drops; null outside a lobby match. */
    public synchronized RejoinTicket getRejoinTicket() {
        return rejoinTicket;
    }

    public synchronized void setRejoinTicket(RejoinTicket ticket) {
        this.rejoinTicket = ticket;
    }

    public synchronized void clearRejoinTicket() {
        this.rejoinTicket = null;
    }

    public synchronized Phase getPhase() {
        return phase;
    }

    public synchronized String getFailure() {
        return failure;
    }

    /** The connected client, or null unless the phase is READY. */
    public synchronized LobbyClient getClient() {
        return phase == Phase.READY ? client : null;
    }

    /**
     * Lets go of the client without closing it — its connection is being taken over by a match
     * (see LobbyClient.takeMatchConnection) — and returns to IDLE.
     */
    public synchronized void release() {
        client = null;
        arrived = null;
        arrivedFailure = null;
        generation++;
        phase = Phase.IDLE;
    }

    /** Closes the connection (and abandons one still being dialled). */
    @Override
    public synchronized void close() {
        closeClient();
        generation++;
        arrived = null;
        arrivedFailure = null;
        phase = Phase.IDLE;
    }

    private void closeClient() {
        if (client != null) client.close();
        client = null;
    }
}
