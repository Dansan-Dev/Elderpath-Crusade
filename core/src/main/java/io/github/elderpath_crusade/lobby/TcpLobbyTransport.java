package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.multiplayer.net.NetworkConnection;
import io.github.elderpath_crusade.net.transport.ConnectionLimiter;
import io.github.elderpath_crusade.net.transport.LineLink;
import io.github.elderpath_crusade.net.transport.ProxyProtocol;
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
 * queue length), a cap on open connections (each costs a reader thread), and per-address limits
 * on open connections and on new ones per minute, with escalating blocks ({@link ConnectionLimiter}). TLS is terminated in
 * front of this (the hosting platform's proxy); this class speaks plain TCP.
 */
public final class TcpLobbyTransport implements LobbyTransport {
    /** Browsing players hold a connection while they look at the list; more than a match server allows. */
    static final int MAX_OPEN_CONNECTIONS = 64;
    /** Connections still waiting to send their PROXY header at once (each holds a thread briefly). */
    static final int MAX_HANDSHAKING = 32;
    static final int PROXY_HEADER_TIMEOUT_MS = 3_000;

    private final ServerSocket serverSocket;
    private final Queue<LineLink> accepted = new ConcurrentLinkedQueue<>();
    private final List<NetworkConnection> connections = new CopyOnWriteArrayList<>();
    private final ConnectionLimiter limiter = new ConnectionLimiter(ConnectionLimiter.Config.defaults());
    private final boolean proxyProtocol;
    private final java.util.concurrent.atomic.AtomicInteger handshaking = new java.util.concurrent.atomic.AtomicInteger();

    /** Plain: clients connect directly, so the socket's own address identifies them. */
    public TcpLobbyTransport(String bindAddress, int port) throws IOException {
        this(bindAddress, port, false);
    }

    /**
     * proxyProtocol: every connection must start with a PROXY protocol v1 line from the hosting
     * platform's proxy naming the real client address (see {@link ProxyProtocol}); connections
     * without one are closed. Enable only when the proxy is the sole way in.
     */
    public TcpLobbyTransport(String bindAddress, int port, boolean proxyProtocol) throws IOException {
        this.proxyProtocol = proxyProtocol;
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
                if (proxyProtocol) {
                    // The header is read off the accept thread, with a timeout, so a client that
                    // never sends it can't hold up everyone else.
                    if (handshaking.incrementAndGet() > MAX_HANDSHAKING) {
                        handshaking.decrementAndGet();
                        socket.close();
                        continue;
                    }
                    Thread handshake = new Thread(() -> readProxyHeaderThenAdmit(socket), "lobby-proxy-handshake");
                    handshake.setDaemon(true);
                    handshake.start();
                } else {
                    // Loopback is this machine's own processes (a lobby bridging to its child match servers, a
                    // client to its own server): only the global limit applies, never a per-address one.
                    String address = socket.getInetAddress() == null || socket.getInetAddress().isLoopbackAddress()
                            ? null : socket.getInetAddress().getHostAddress();
                    admit(socket, address, address);
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) Logger.error("TcpLobbyTransport", "Accept failed: " + e.getMessage());
            }
        }
    }

    private void readProxyHeaderThenAdmit(Socket socket) {
        try {
            socket.setSoTimeout(PROXY_HEADER_TIMEOUT_MS);
            String clientAddress = ProxyProtocol.read(socket.getInputStream());
            socket.setSoTimeout(0);
            admit(socket, clientAddress, clientAddress);
        } catch (IOException e) {
            closeQuietly(socket);
        } finally {
            handshaking.decrementAndGet();
        }
    }

    /** Applies the connection limits for limitKey (null = only the global one) and queues the connection if allowed. */
    private void admit(Socket socket, String limitKey, String reportedAddress) {
        try {
            synchronized (limiter) {
                connections.removeIf(c -> !c.isConnected());
                int openFromAddress = (int) connections.stream().filter(c -> limitKey != null && limitKey.equals(c.peerAddress())).count();
                if (connections.size() >= MAX_OPEN_CONNECTIONS || !limiter.allow(limitKey, openFromAddress, System.nanoTime())) {
                    socket.close();
                    return;
                }
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                NetworkConnection connection = new NetworkConnection(socket,
                        NetworkConnection.SERVER_MAX_LINE_CHARS, NetworkConnection.SERVER_MAX_QUEUED_LINES, reportedAddress);
                connections.add(connection);
                accepted.add(connection);
            }
        } catch (IOException e) {
            closeQuietly(socket);
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
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
