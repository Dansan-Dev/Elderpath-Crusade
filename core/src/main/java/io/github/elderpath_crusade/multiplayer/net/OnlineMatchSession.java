package io.github.elderpath_crusade.multiplayer.net;

import io.github.elderpath_crusade.enums.PieceAlignment;

import java.io.IOException;

/**
 * Tracks this process's role in an online match: hosting is always P1, joining as guest is
 * always P2 — a fixed assignment, no negotiation (matches this prototype's scope). Held on
 * GameContext; ActionDispatcher consults it to decide whether a local click executes
 * in-process (host) or sends a NetworkCommand (guest).
 */
public class OnlineMatchSession {
    /** Fixed port for this prototype — no port entry/negotiation UI yet. */
    public static final int DEFAULT_PORT = 4567;

    private GameHost host;
    private GameClient client;

    public boolean isActive() {
        return host != null || client != null;
    }

    public boolean isHost() {
        return host != null;
    }

    public boolean isGuest() {
        return client != null;
    }

    public GameHost getHost() {
        return host;
    }

    public GameClient getClient() {
        return client;
    }

    public PieceAlignment getLocalAlignment() {
        if (host != null) return PieceAlignment.P1;
        if (client != null) return PieceAlignment.P2;
        return null;
    }

    public void startHosting(int port) throws IOException {
        stop();
        host = new GameHost();
        host.start(port);
    }

    public void joinHost(String address, int port) throws IOException {
        stop();
        client = new GameClient(address, port);
    }

    /** Poll every frame — drains the transport's incoming queue on the main thread. */
    public void update() {
        if (host != null) host.update();
        if (client != null) client.update();
    }

    public void stop() {
        if (host != null) {
            host.stop();
            host = null;
        }
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
