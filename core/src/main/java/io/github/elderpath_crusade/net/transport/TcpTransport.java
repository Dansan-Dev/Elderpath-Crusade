package io.github.elderpath_crusade.net.transport;

import io.github.elderpath_crusade.multiplayer.net.NetworkConnection;
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
import java.util.function.Consumer;

/**
 * Clients over TCP: a daemon accept thread wraps each socket in a NetworkConnection (line
 * framing + its own reader thread) and queues it for the server's tick to pick up. Bind to
 * 127.0.0.1 for a solo match's child-process server, to all interfaces to host.
 */
public final class TcpTransport implements ServerTransport {
    private final ServerSocket serverSocket;
    private final Queue<ClientChannel> accepted = new ConcurrentLinkedQueue<>();
    private final List<NetworkConnection> connections = new CopyOnWriteArrayList<>();

    /** port 0 picks a free ephemeral port (see getPort()). */
    public TcpTransport(String bindAddress, int port) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName(bindAddress), port));
        Thread acceptThread = new Thread(this::acceptLoop, "match-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /** Opens a client connection to a match server listening at host:port. */
    public static ServerConnection connect(String host, int port, Consumer<String> rawTap) throws IOException {
        Socket socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        return LineChannels.serverConnection(new NetworkConnection(socket), rawTap);
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                NetworkConnection connection = new NetworkConnection(socket);
                connections.add(connection);
                accepted.add(LineChannels.clientChannel(connection));
            } catch (IOException e) {
                if (!serverSocket.isClosed()) Logger.error("TcpTransport", "Accept failed: " + e.getMessage());
            }
        }
    }

    @Override
    public ClientChannel accept() {
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
