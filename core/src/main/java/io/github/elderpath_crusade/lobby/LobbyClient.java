package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.lobby.LobbyServerMessage.*;
import io.github.elderpath_crusade.multiplayer.net.NetworkConnection;
import io.github.elderpath_crusade.net.protocol.ProtocolException;
import io.github.elderpath_crusade.net.transport.LineChannels;
import io.github.elderpath_crusade.net.transport.LineLink;
import io.github.elderpath_crusade.net.transport.ServerConnection;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * A client's side of the lobby service: its connection plus what the server has told it — the
 * list of open lobbies, the lobby it is in, errors, and finally which seat it has in a started
 * match. Like MatchClient it holds no rules; every call is a request and the state only changes
 * when the server answers. Not thread-safe: poll() and the requests belong to one loop.
 *
 * When MatchStarting arrives the connection carries the match protocol from then on:
 * {@link #takeMatchConnection()} hands it over (and this client stops reading it).
 */
public final class LobbyClient implements AutoCloseable {
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private final LineLink link;
    private boolean welcomed;
    private List<LobbyView> lobbies = List.of();
    private LobbyView lobby;
    private boolean host;
    private LobbyError lastError;
    private String closedReason;
    private String matchSeat;
    private String rejoinToken;
    private String joinedLobbyId;
    private boolean handedOver;
    private boolean protocolFailed;

    /** Says hello as playerName on an open link. */
    public LobbyClient(LineLink link, String playerName) {
        this.link = link;
        link.send(LobbyCodec.encode(new LobbyClientMessage.LobbyHello(LobbyCodec.LOBBY_PROTOCOL_VERSION, playerName)));
    }

    /** Opens a TCP connection (TLS when tls is set — the public server's front proxy terminates it) and says hello. */
    public static LobbyClient connect(String host, int port, boolean tls, String playerName) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        if (tls) {
            SSLSocket secure = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, host, port, true);
            // Without this the JDK accepts a certificate for any host name — the server's own must match.
            SSLParameters parameters = secure.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            secure.setSSLParameters(parameters);
            secure.startHandshake();
            socket = secure;
        }
        socket.setTcpNoDelay(true);
        return new LobbyClient(new NetworkConnection(socket), playerName);
    }

    /** Applies everything the server has sent since the last call; returns how many messages that was. */
    public int poll() {
        if (handedOver) return 0;
        int count = 0;
        String line;
        while (!handedOver && (line = link.pollLine()) != null) {
            LobbyServerMessage message;
            try {
                message = LobbyCodec.decodeServer(line);
            } catch (ProtocolException e) {
                protocolFailed = true;
                lastError = new LobbyError(LobbyError.Code.BAD_MESSAGE, "The server sent something unreadable");
                link.close();
                return count;
            }
            apply(message);
            count++;
        }
        return count;
    }

    private void apply(LobbyServerMessage message) {
        if (message instanceof LobbyWelcome) {
            welcomed = true;
        } else if (message instanceof LobbyList l) {
            lobbies = List.copyOf(l.lobbies());
        } else if (message instanceof LobbyJoined j) {
            lobby = j.lobby();
            joinedLobbyId = j.lobby().id();
            host = j.host();
            lastError = null;
        } else if (message instanceof LobbyUpdate u) {
            lobby = u.lobby();
        } else if (message instanceof LobbyClosed c) {
            lobby = null;
            host = false;
            closedReason = c.reason();
        } else if (message instanceof MatchStarting m) {
            matchSeat = m.seat();
            if (m.rejoinToken() != null) rejoinToken = m.rejoinToken();
        } else if (message instanceof LobbyError e) {
            lastError = e;
        }
    }

    // --- requests ---

    public void requestList() {
        send(new LobbyClientMessage.ListLobbies());
    }

    /** password null or empty = an open lobby. deck is the card names of the deck to play. */
    public void createLobby(String name, String password, List<String> deck) {
        lastError = null;
        send(new LobbyClientMessage.CreateLobby(name, password, deck));
    }

    public void joinLobby(String lobbyId, String password, List<String> deck) {
        lastError = null;
        send(new LobbyClientMessage.JoinLobby(lobbyId, password, deck));
    }

    public void leaveLobby() {
        send(new LobbyClientMessage.LeaveLobby());
    }

    /** Host only. */
    public void startMatch() {
        lastError = null;
        send(new LobbyClientMessage.StartMatch());
    }

    /** After a dropped connection, on a fresh client: asks to be put back into the running match. */
    public void rejoinMatch(String lobbyId, String rejoinToken) {
        lastError = null;
        send(new LobbyClientMessage.RejoinMatch(lobbyId, rejoinToken));
    }

    private void send(LobbyClientMessage message) {
        if (!handedOver) link.send(LobbyCodec.encode(message));
    }

    // --- state ---

    public boolean isWelcomed() {
        return welcomed;
    }

    public boolean isConnected() {
        return !protocolFailed && link.isOpen();
    }

    /** The open lobbies as of the last LobbyList. */
    public List<LobbyView> getLobbies() {
        return lobbies;
    }

    /** The lobby this client is in, or null. */
    public LobbyView getLobby() {
        return lobby;
    }

    /** Whether this client may start the match of its lobby. */
    public boolean isHost() {
        return host;
    }

    /** The last refusal since the last create/join/start request, or null. */
    public LobbyError getLastError() {
        return lastError;
    }

    /** Why the lobby this client was in closed, or null. */
    public String getClosedReason() {
        return closedReason;
    }

    /** The id of the lobby this client was last in (kept after the match starts, for rejoining), or null. */
    public String getJoinedLobbyId() {
        return joinedLobbyId;
    }

    /** The secret that brings this player back into the running match after a drop, once MatchStarting arrived; else null. */
    public String getRejoinToken() {
        return rejoinToken;
    }

    /** "P1"/"P2" once the match is starting — then call takeMatchConnection(). */
    public String getMatchSeat() {
        return matchSeat;
    }

    /**
     * The connection for the match protocol (send the match Hello for getMatchSeat() on it).
     * After this the client no longer reads or writes it.
     */
    public ServerConnection takeMatchConnection() {
        if (matchSeat == null) throw new IllegalStateException("no match is starting");
        if (handedOver) throw new IllegalStateException("the match connection was already taken");
        handedOver = true;
        return LineChannels.serverConnection(link, null);
    }

    @Override
    public void close() {
        if (!handedOver) link.close();
    }
}
