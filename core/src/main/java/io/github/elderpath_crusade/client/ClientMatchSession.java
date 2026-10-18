package io.github.elderpath_crusade.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import io.github.elderpath_crusade.net.transport.ServerConnection;
import io.github.elderpath_crusade.net.transport.TcpTransport;
import io.github.elderpath_crusade.server.ServerProcess;
import io.github.elderpath_crusade.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A game screen's handle on one match, which is always run by a match server: a child process
 * this client launches (solo, hotseat, hosting) or a server elsewhere (joining). It owns the
 * screen's connection(s) — one per seat it plays; two for hotseat — and their ClientMatchState
 * replicas, and sends ClientCommands through the connection of the seat that is acting.
 *
 * Threading: launching the server and opening sockets block (the server takes a second or two
 * to start), so they run on a background thread; {@link #getStatus()} reports progress. All
 * protocol work — creating each MatchClient, sending, polling, reading state — happens on the
 * thread that calls {@link #poll()} (the render thread), like everything else touching the game.
 */
public final class ClientMatchSession implements ClientCommands, AutoCloseable {
    private static final String LOOPBACK = "127.0.0.1";
    /** System property overriding where a launched server reads its assets. */
    public static final String ASSETS_PROPERTY = "elderpath.assetsDir";

    public enum Phase {
        /** Launching the local match server. */
        STARTING_SERVER,
        /** Connecting to the server and asking for seats. */
        CONNECTING,
        /** Seated; the match starts once every human seat is taken (e.g. a host waiting for its guest). */
        WAITING_FOR_PLAYERS,
        PLAYING,
        /** The match ended (see {@link #getWinner()}). */
        ENDED,
        /** The server couldn't be started or reached, or refused a seat (see the detail). */
        FAILED,
        /** The local server process exited before the match ended. */
        SERVER_EXITED,
        /** A connection closed before the match ended. */
        DISCONNECTED,
        CLOSED
    }

    public record Status(Phase phase, String detail) {
        public boolean isReady() {
            return phase == Phase.PLAYING;
        }

        public boolean isOver() {
            return switch (phase) {
                case ENDED, FAILED, SERVER_EXITED, DISCONNECTED, CLOSED -> true;
                default -> false;
            };
        }
    }

    /** A seat this screen plays and the connection it plays it through. */
    private record Seat(SeatRequest request, MatchClient client) {
    }

    private final String playerName;
    private final List<SeatRequest> seatRequests;
    /** Whether this session launches its own server (startLocal/host) rather than joining one. */
    private final boolean launchesServer;
    private final List<Seat> seats = new ArrayList<>();

    // Written by the startup thread, read on the poll thread; guarded by this.
    private ServerProcess process;
    private List<ServerConnection> pendingConnections;
    private String failure;
    private boolean connecting;
    private boolean closed;
    private int port = -1;

    private ClientMatchSession(String playerName, List<SeatRequest> seatRequests, boolean launchesServer) {
        this.playerName = playerName;
        this.seatRequests = List.copyOf(seatRequests);
        this.launchesServer = launchesServer;
    }

    /**
     * Launches a match server on this machine (loopback only, any free port) and plays every
     * human seat of spec from this screen — one seat for solo vs a bot, both for hotseat.
     */
    public static ClientMatchSession startLocal(LocalMatchSpec spec) {
        List<SeatRequest> requests = new ArrayList<>();
        for (PieceAlignment side : spec.humanSides()) requests.add(SeatRequest.valueOf(side.name()));
        return launch(spec, LOOPBACK, 0, requests);
    }

    /**
     * Launches a match server others can reach (bindAddress, e.g. 0.0.0.0, on port — 0 = any
     * free port, see {@link #getPort()}) and plays P1 from this screen; the other human seat is
     * left for a remote guest to {@link #join}.
     */
    public static ClientMatchSession host(LocalMatchSpec spec, String bindAddress, int port) {
        if (!spec.humanSides().contains(PieceAlignment.P1)) throw new IllegalArgumentException("the host plays P1");
        return launch(spec, bindAddress, port, List.of(SeatRequest.P1));
    }

    /** Connects to a match server elsewhere and asks for seat (ANY = whichever is free). */
    public static ClientMatchSession join(String host, int port, SeatRequest seat, String playerName) {
        ClientMatchSession session = new ClientMatchSession(playerName, List.of(seat), false);
        session.startInBackground(() -> session.connectAll(host, port));
        return session;
    }

    private static ClientMatchSession launch(LocalMatchSpec spec, String bindAddress, int port, List<SeatRequest> requests) {
        if (requests.isEmpty()) throw new IllegalArgumentException("no human seat to play: " + spec.bots());
        ClientMatchSession session = new ClientMatchSession(spec.playerName(), requests, true);
        session.startInBackground(() -> {
            ServerProcess server = ServerProcess.start(ServerProcess.Options.of(serverArgs(spec, bindAddress, port)));
            if (session.adoptProcess(server)) session.connectAll(LOOPBACK, server.getPort());
        });
        return session;
    }

    private static String[] serverArgs(LocalMatchSpec spec, String bindAddress, int port) {
        List<String> args = new ArrayList<>(List.of("--bind", bindAddress, "--port", Integer.toString(port)));
        File assets = spec.assetsDir() != null ? spec.assetsDir() : resolveAssetsDir();
        if (assets != null) {
            args.add("--assets");
            args.add(assets.getAbsolutePath());
        }
        args.addAll(spec.launcherArgs());
        return args.toArray(new String[0]);
    }

    /**
     * Where a launched server should read its assets: the {@value #ASSETS_PROPERTY} property if
     * set, else the directory this game reads data/pieces.yaml from (the dev layout, run from
     * assets/), else null — the server then falls back to its classpath, which in a packaged
     * game is the same jar that carries this game's assets.
     */
    static File resolveAssetsDir() {
        String configured = System.getProperty(ASSETS_PROPERTY);
        if (configured != null) return new File(configured);
        if (Gdx.files == null) return null;
        try {
            FileHandle pieces = Gdx.files.internal("data/pieces.yaml");
            File file = pieces.file();
            if (file != null && file.isFile()) return file.getAbsoluteFile().getParentFile().getParentFile();
        } catch (RuntimeException ignored) {
            // A classpath-backed internal file has no File — let the server use its classpath.
        }
        return null;
    }

    // ---------------------------------------------------------------- startup (background thread)

    private interface Startup {
        void run() throws Exception;
    }

    private void startInBackground(Startup body) {
        Thread thread = new Thread(() -> {
            try {
                body.run();
            } catch (Exception e) {
                fail(e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }, "match-session-startup");
        thread.setDaemon(true);
        thread.start();
    }

    /** Keeps the launched server, unless the session was closed meanwhile (then stops it). */
    private synchronized boolean adoptProcess(ServerProcess server) {
        if (closed) {
            server.stop();
            return false;
        }
        process = server;
        port = server.getPort();
        connecting = true;
        return true;
    }

    private void connectAll(String host, int serverPort) throws IOException {
        synchronized (this) {
            connecting = true;
            if (port < 0) port = serverPort;
        }
        List<ServerConnection> connections = new ArrayList<>();
        try {
            for (int i = 0; i < seatRequests.size(); i++) connections.add(TcpTransport.connect(host, serverPort, null));
        } catch (IOException e) {
            connections.forEach(ServerConnection::close);
            throw new IOException("can't reach the match server at " + host + ":" + serverPort + ": " + e.getMessage(), e);
        }
        synchronized (this) {
            if (closed) {
                connections.forEach(ServerConnection::close);
                return;
            }
            pendingConnections = connections;
        }
    }

    private synchronized void fail(String reason) {
        if (closed) return;
        Logger.error("ClientMatchSession", reason);
        failure = reason;
    }

    // ---------------------------------------------------------------- poll thread

    /**
     * Call every frame: takes over the connections once they're open (asking for this screen's
     * seats), then applies everything the server has sent since the last call. Never blocks.
     */
    public void poll() {
        List<ServerConnection> ready = takePendingConnections();
        if (ready != null) {
            for (int i = 0; i < ready.size(); i++) {
                seats.add(new Seat(seatRequests.get(i), new MatchClient(ready.get(i), seatRequests.get(i), playerName)));
            }
        }
        for (Seat seat : seats) seat.client().poll();
    }

    private synchronized List<ServerConnection> takePendingConnections() {
        List<ServerConnection> out = pendingConnections;
        pendingConnections = null;
        return out;
    }

    /** Where the session is — starting, waiting, playing, over. Call from the poll thread. */
    public synchronized Status getStatus() {
        if (closed) return new Status(Phase.CLOSED, "");
        if (failure != null) return new Status(Phase.FAILED, failure);
        if (seats.isEmpty()) {
            boolean launching = launchesServer && !connecting;
            return new Status(launching ? Phase.STARTING_SERVER : Phase.CONNECTING, "");
        }
        for (Seat seat : seats) {
            ClientMatchState state = seat.client().getState();
            ServerMessage.Error error = state.getLastError();
            if (!state.isWelcomed() && error != null) return new Status(Phase.FAILED, error.code() + ": " + error.message());
        }
        if (anyState(ClientMatchState::isEnded)) {
            PieceAlignment winner = getWinner();
            return new Status(Phase.ENDED, winner != null ? winner.name() : "NONE");
        }
        if (process != null && !process.isAlive()) return new Status(Phase.SERVER_EXITED, "the match server stopped");
        for (Seat seat : seats) {
            if (!seat.client().isConnected()) return new Status(Phase.DISCONNECTED, "lost the connection to the match server");
        }
        boolean allSeated = seats.stream().allMatch(s -> s.client().getState().isWelcomed());
        if (!allSeated) return new Status(Phase.CONNECTING, "");
        return anyState(ClientMatchState::isStarted)
                ? new Status(Phase.PLAYING, "")
                : new Status(Phase.WAITING_FOR_PLAYERS, "");
    }

    private boolean anyState(Predicate<ClientMatchState> test) {
        for (Seat seat : seats) if (test.test(seat.client().getState())) return true;
        return false;
    }

    /** The port of the match server (the one launched here, or the one joined); -1 until known. */
    public synchronized int getPort() {
        return port;
    }

    /** The sides this screen plays, in seat order, once the server has seated them. */
    public List<PieceAlignment> getControlledSeats() {
        List<PieceAlignment> out = new ArrayList<>();
        for (Seat seat : seats) {
            PieceAlignment side = seat.client().getState().getSeat();
            if (side != null) out.add(side);
        }
        return Collections.unmodifiableList(out);
    }

    public boolean controls(PieceAlignment side) {
        return side != null && getControlledSeats().contains(side);
    }

    /** The side this screen is acting as right now: the current player, if it's one of ours; else null. */
    public PieceAlignment actingSeat() {
        PieceAlignment current = currentPlayer();
        return controls(current) ? current : null;
    }

    /** The replica of the connection playing side, or null if this screen doesn't play it. */
    public ClientMatchState stateFor(PieceAlignment side) {
        MatchClient client = clientFor(side);
        return client != null ? client.getState() : null;
    }

    /**
     * The replica the screen should show: the acting seat's (in hotseat, whoever's turn it is),
     * else this screen's first seat. Null until connected.
     */
    public ClientMatchState viewState() {
        PieceAlignment acting = actingSeat();
        if (acting != null) return stateFor(acting);
        return seats.isEmpty() ? null : seats.get(0).client().getState();
    }

    public PieceAlignment currentPlayer() {
        for (Seat seat : seats) {
            PieceAlignment current = seat.client().getState().getCurrentPlayer();
            if (current != null) return current;
        }
        return null;
    }

    public PieceAlignment getWinner() {
        for (Seat seat : seats) {
            if (seat.client().getState().isEnded()) return seat.client().getState().getWinner();
        }
        return null;
    }

    /** Whether any command this screen sent hasn't been acknowledged by the server yet. */
    public boolean hasUnackedCommands() {
        for (Seat seat : seats) if (seat.client().hasUnackedCommands()) return true;
        return false;
    }

    private MatchClient clientFor(PieceAlignment side) {
        if (side == null) return null;
        for (Seat seat : seats) {
            if (side == seat.client().getState().getSeat()) return seat.client();
        }
        return null;
    }

    // ---------------------------------------------------------------- ClientCommands

    @Override
    public long moveOrAttack(int srcRow, int srcCol, int dstRow, int dstCol) {
        return viaActingSeat(c -> c.moveOrAttack(srcRow, srcCol, dstRow, dstCol));
    }

    @Override
    public long playSummon(String instanceId, int row, int col) {
        return viaActingSeat(c -> c.playSummon(instanceId, row, col));
    }

    @Override
    public long playSpell(String instanceId, Integer targetRow, Integer targetCol) {
        return viaActingSeat(c -> c.playSpell(instanceId, targetRow, targetCol));
    }

    @Override
    public long activateAbility(String pieceId, int actionIndex) {
        return viaActingSeat(c -> c.activateAbility(pieceId, actionIndex));
    }

    @Override
    public long answerPick(Coord choice) {
        MatchClient client = clientWithPendingPick();
        return client != null ? client.answerPick(choice) : NOT_SENT;
    }

    @Override
    public long cancelPick() {
        MatchClient client = clientWithPendingPick();
        return client != null ? client.cancelPick() : NOT_SENT;
    }

    @Override
    public long endTurn() {
        return viaActingSeat(MatchClient::endTurn);
    }

    /** The pick this screen has been asked to answer (a pick goes only to its owner's connection), or null. */
    public PickRequest pendingPick() {
        MatchClient client = clientWithPendingPick();
        return client != null ? client.getState().getPendingPick() : null;
    }

    private long viaActingSeat(Function<MatchClient, Long> send) {
        MatchClient client = clientFor(actingSeat());
        if (client == null || !client.isConnected()) return NOT_SENT;
        return send.apply(client);
    }

    /** The connection holding a pending pick — the acting seat's first, since picks go to their owner. */
    private MatchClient clientWithPendingPick() {
        MatchClient acting = clientFor(actingSeat());
        if (acting != null && acting.getState().getPendingPick() != null && acting.isConnected()) return acting;
        for (Seat seat : seats) {
            if (seat.client().getState().getPendingPick() != null && seat.client().isConnected()) return seat.client();
        }
        return null;
    }

    // ---------------------------------------------------------------- shutdown

    /** Leaves the match and stops the server this session launched (if any). Safe to call more than once. */
    @Override
    public void close() {
        ServerProcess server;
        List<ServerConnection> unclaimed;
        synchronized (this) {
            if (closed) return;
            closed = true;
            server = process;
            unclaimed = pendingConnections;
            pendingConnections = null;
        }
        for (Seat seat : seats) {
            if (seat.client().isConnected()) seat.client().leave();
        }
        if (unclaimed != null) unclaimed.forEach(ServerConnection::close);
        if (server != null) server.stop();
    }

    /** Whether the server this session launched is still running (false for a join session). */
    public synchronized boolean isServerAlive() {
        return process != null && process.isAlive();
    }
}
