package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.multiplayer.net.NetworkConnection;
import io.github.elderpath_crusade.net.transport.LineLink;
import io.github.elderpath_crusade.utils.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lobby clients over TCP, with the same bounds as the match server's transport (line length,
 * queue length) and a cap on open connections — each costs a reader thread. TLS is terminated in
 * front of this (the hosting platform's proxy); this class speaks plain TCP.
 */
public final class TcpLobbyTransport implements LobbyTransport {
    /** Browsing players hold a connection while they look at the list; more than a match server allows. */
    static final int MAX_OPEN_CONNECTIONS = 64;

    private final ServerSocket serverSocket;
    private final Queue<LineLink> accepted = new ConcurrentLinkedQueue<>();
    private final List<NetworkConnection> connections = new CopyOnWriteArrayList<>();

    public TcpLobbyTransport(String bindAddress, int port) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName(bindAddress), port));
        Thread acceptThread = new Thread(this::acceptLoop, "lobby-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                connections.removeIf(c -> !c.isConnected());
                if (connections.size() >= MAX_OPEN_CONNECTIONS) {
                    socket.close();
                    continue;
                }
                socket.setTcpNoDelay(true);
                NetworkConnection connection = new NetworkConnection(socket,
                        NetworkConnection.SERVER_MAX_LINE_CHARS, NetworkConnection.SERVER_MAX_QUEUED_LINES);
                connections.add(connection);
                accepted.add(connection);
            } catch (IOException e) {
                if (!serverSocket.isClosed()) Logger.error("TcpLobbyTransport", "Accept failed: " + e.getMessage());
            }
        }
    }

    @Override
    public LineLink accept() {
        return accepted.poll();
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        for (NetworkConnection connection : connections) connection.close();
        connections.clear();
    }
}
