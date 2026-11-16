package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.lobby.LobbyClientMessage.*;
import io.github.elderpath_crusade.lobby.LobbyServerMessage.LobbyError.Code;
import io.github.elderpath_crusade.net.protocol.ProtocolException;
import io.github.elderpath_crusade.net.transport.AttemptLimiter;
import io.github.elderpath_crusade.net.transport.LineLink;
import io.github.elderpath_crusade.utils.Logger;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The lobby service of the dedicated server: players host and join named, optionally password
 * protected lobbies; the host starts the match, which runs in a match server the MatchLauncher
 * provides, and from then on this service only forwards lines between each player and it.
 *
 * Clients are untrusted, exactly as for the match server: a connection must say hello within
 * HELLO_TIMEOUT_NANOS, handles at most MAX_MESSAGES_PER_TICK messages per tick, is dropped after
 * MAX_BAD_MESSAGES unreadable ones or MAX_PASSWORD_ATTEMPTS wrong passwords, and can neither
 * hold more than one lobby nor create lobbies without limit. Passwords exist only as salted
 * PBKDF2 hashes in memory. Everything runs on the single thread that calls tick().
 */
public final class LobbyServer {
    static final long HELLO_TIMEOUT_NANOS = 10_000_000_000L;
    static final long LOBBY_IDLE_NANOS = 15L * 60 * 1_000_000_000L;
    static final long LAUNCH_TIMEOUT_NANOS = 30_000_000_000L;
    /** How long a running match waits for a dropped player to come back before the other player wins. */
    static final long REJOIN_HOLD_NANOS = 20L * 60 * 1_000_000_000L;
    /** A taken-over seat's old match connection needs this long to be noticed by the match server before the new one says hello. */
    static final long TAKEOVER_DELAY_NANOS = 300_000_000L;
    static final int MAX_MESSAGES_PER_TICK = 32;
    static final int MAX_BAD_MESSAGES = 5;
    static final int MAX_PASSWORD_ATTEMPTS = 5;
    static final int MAX_LOBBIES = 32;
    static final int MAX_PLAYER_NAME = 24;
    static final int MAX_LOBBY_NAME = 32;
    static final int MAX_PASSWORD = 64;
    static final int MAX_DECK_CARDS = 60;
    static final int MAX_LISTED_LOBBIES = 50;
    private static final int PBKDF2_ITERATIONS = 20_000;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long SECOND = 1_000_000_000L;
    private static final long PRUNE_INTERVAL_NANOS = 60 * SECOND;
    private static final String LEAVE_PREFIX = "{\"type\":\"Leave\"";
    private static final String MATCH_ENDED_PREFIX = "{\"type\":\"MatchEnded\"";

    private enum LobbyState { OPEN, STARTING, IN_MATCH }

    private static final class Conn {
        final LineLink link;
        final long deadlineNanos;
        String playerName;
        Lobby lobby;
        int badMessages;
        int wrongPasswords;
        /** Once set, the connection carries the match protocol: lines are forwarded, not read. */
        LineLink upstream;
        /** Said Leave in the match protocol: the player means to go, not to come back. */
        boolean leftMatch;
        /** Rejoining: waiting out the takeover delay before it is bridged. */
        boolean awaitingAttach;

        Conn(LineLink link, long deadlineNanos) {
            this.link = link;
            this.deadlineNanos = deadlineNanos;
        }
    }

    /** One player's place in a running match: who holds it, and what it takes to come back to it. */
    private static final class SeatSlot {
        final String seatName;
        final String rejoinToken = newToken();
        Conn conn;
        /** A rejoining connection waiting for pendingAttachNanos. */
        Conn pending;
        long pendingAttachNanos;
        long vacantSince;

        SeatSlot(String seatName) {
            this.seatName = seatName;
        }
    }

    private static final class Lobby {
        final String id = newId();
        final String name;
        final Conn host;
        Conn guest;
        final List<String> hostDeck;
        List<String> guestDeck;
        final byte[] salt;
        final byte[] passwordHash;
        LobbyState state = LobbyState.OPEN;
        long lastActivityNanos;
        CompletableFuture<MatchLauncher.MatchEndpoint> launch;
        long launchDeadlineNanos;
        MatchLauncher.MatchEndpoint endpoint;
        final SeatSlot[] slots = {new SeatSlot("P1"), new SeatSlot("P2")};
        /** The match server said the match is over: nothing to hold or rejoin any more. */
        boolean matchEnded;

        Lobby(String name, Conn host, List<String> hostDeck, byte[] salt, byte[] passwordHash, long now) {
            this.name = name;
            this.host = host;
            this.hostDeck = hostDeck;
            this.salt = salt;
            this.passwordHash = passwordHash;
            this.lastActivityNanos = now;
        }

        boolean passwordProtected() {
            return passwordHash != null;
        }

        List<Conn> members() {
            return guest == null ? List.of(host) : List.of(host, guest);
        }
    }

    private final MatchLauncher launcher;
    private final LobbyTransport transport;
    private final int maxMatches;
    private final Predicate<String> knownCard;
    private LongSupplier nanoClock = System::nanoTime;

    private final List<Conn> connections = new ArrayList<>();
    private final Map<String, Lobby> lobbies = new LinkedHashMap<>();
    /**
     * Guessing limits, beyond a connection's own strikes (a guesser just reconnects): failed
     * passwords and failed rejoin tokens per client address, and failed passwords per lobby.
     * Each lockout doubles when earned again.
     */
    private final AttemptLimiter addressFailures = new AttemptLimiter(10, 10 * 60 * SECOND, 60 * SECOND, 60 * 60 * SECOND);
    private final AttemptLimiter lobbyFailures = new AttemptLimiter(5, 5 * 60 * SECOND, 60 * SECOND, 15 * 60 * SECOND);
    private long lastPruneNanos;

    /** maxMatches: matches (started lobbies) running at once; knownCard: whether a card name can appear in a deck. */
    public LobbyServer(LobbyTransport transport, MatchLauncher launcher, int maxMatches, Predicate<String> knownCard) {
        this.transport = transport;
        this.launcher = launcher;
        this.maxMatches = maxMatches;
        this.knownCard = knownCard;
    }

    /** Replaces the clock timeouts are measured on (tests). */
    void setNanoClock(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    /** One step: accept, read messages, finish launches, forward match lines, expire lobbies. */
    public void tick() {
        long now = nanoClock.getAsLong();
        LineLink link;
        while ((link = transport.accept()) != null) connections.add(new Conn(link, now + HELLO_TIMEOUT_NANOS));

        for (Iterator<Conn> it = connections.iterator(); it.hasNext(); ) {
            Conn conn = it.next();
            if (conn.upstream != null) {
                forward(conn);
                if (conn.leftMatch && conn.lobby != null) forfeit(conn.lobby, conn);
            } else if (conn.awaitingAttach) {
                // waiting for its seat to be handed over — its lines stay queued until then
            } else {
                drainMessages(conn, now);
            }
            if (!conn.link.isOpen() || (conn.upstream != null && !conn.upstream.isOpen())) {
                disconnect(conn);
                it.remove();
            }
        }
        finishLaunches(now);
        runMatchUpkeep(now);
        expireLobbies(now);
        if (now - lastPruneNanos >= PRUNE_INTERVAL_NANOS) {
            lastPruneNanos = now;
            addressFailures.prune(now);
            lobbyFailures.prune(now);
        }
    }

    public int lobbyCount() {
        return lobbies.size();
    }

    public int activeMatches() {
        int count = 0;
        for (Lobby lobby : lobbies.values()) if (lobby.state != LobbyState.OPEN) count++;
        return count;
    }

    public void close() {
        for (Conn conn : connections) {
            conn.link.close();
            if (conn.upstream != null) conn.upstream.close();
        }
        for (Lobby lobby : lobbies.values()) {
            if (lobby.state == LobbyState.STARTING) closeLateEndpoint(lobby);
            if (lobby.endpoint != null) lobby.endpoint.close();
        }
        connections.clear();
        lobbies.clear();
        transport.close();
    }

    // --- reading ---

    private void drainMessages(Conn conn, long now) {
        for (int handled = 0; handled < MAX_MESSAGES_PER_TICK && conn.upstream == null; handled++) {
            String line = conn.link.pollLine();
            if (line == null) break;
            LobbyClientMessage message;
            try {
                message = LobbyCodec.decodeClient(line);
            } catch (ProtocolException e) {
                send(conn, new LobbyServerMessage.LobbyError(Code.BAD_MESSAGE, e.getMessage()));
                if (++conn.badMessages >= MAX_BAD_MESSAGES) conn.link.close();
                continue;
            }
            try {
                handle(conn, message, now);
            } catch (RuntimeException e) {
                Logger.error("LobbyServer", "Handling " + message.getClass().getSimpleName() + " failed: " + e);
                send(conn, new LobbyServerMessage.LobbyError(Code.BAD_MESSAGE, "Could not handle that message"));
            }
        }
        if (conn.playerName == null && conn.link.isOpen() && now - conn.deadlineNanos >= 0) {
            send(conn, new LobbyServerMessage.LobbyError(Code.NOT_HELLOED, "No hello received"));
            conn.link.close();
        }
    }

    private void handle(Conn conn, LobbyClientMessage message, long now) {
        if (message instanceof LobbyHello hello) {
            hello(conn, hello);
        } else if (conn.playerName == null) {
            error(conn, Code.NOT_HELLOED, "Say hello first");
        } else if (message instanceof ListLobbies) {
            send(conn, new LobbyServerMessage.LobbyList(openLobbyViews()));
        } else if (message instanceof CreateLobby create) {
            create(conn, create, now);
        } else if (message instanceof JoinLobby join) {
            join(conn, join, now);
        } else if (message instanceof LeaveLobby) {
            leave(conn, "You left the lobby");
        } else if (message instanceof StartMatch) {
            start(conn, now);
        } else if (message instanceof RejoinMatch rejoin) {
            rejoin(conn, rejoin, now);
        }
    }

    private void hello(Conn conn, LobbyHello hello) {
        if (conn.playerName != null) {
            error(conn, Code.BAD_MESSAGE, "Already said hello");
        } else if (hello.protocolVersion() != LobbyCodec.LOBBY_PROTOCOL_VERSION) {
            send(conn, new LobbyServerMessage.LobbyError(Code.VERSION_MISMATCH,
                    "Server speaks lobby protocol " + LobbyCodec.LOBBY_PROTOCOL_VERSION));
            conn.link.close();
        } else if (!validText(hello.playerName(), MAX_PLAYER_NAME)) {
            send(conn, new LobbyServerMessage.LobbyError(Code.INVALID_NAME, "Player name must be 1-" + MAX_PLAYER_NAME + " visible characters"));
            conn.link.close();
        } else {
            conn.playerName = hello.playerName().strip();
            send(conn, new LobbyServerMessage.LobbyWelcome(LobbyCodec.LOBBY_PROTOCOL_VERSION));
        }
    }

    private void create(Conn conn, CreateLobby create, long now) {
        if (conn.lobby != null) {
            error(conn, Code.ALREADY_IN_LOBBY, "Leave your current lobby first");
        } else if (!validText(create.name(), MAX_LOBBY_NAME)) {
            error(conn, Code.INVALID_NAME, "Lobby name must be 1-" + MAX_LOBBY_NAME + " visible characters");
        } else if (create.password() != null && create.password().length() > MAX_PASSWORD) {
            error(conn, Code.INVALID_NAME, "Password is too long");
        } else if (!validDeck(create.deck())) {
            error(conn, Code.INVALID_DECK, "That deck is not valid");
        } else if (lobbies.size() >= MAX_LOBBIES) {
            error(conn, Code.TOO_MANY_LOBBIES, "The server has too many lobbies, try again later");
        } else {
            boolean hasPassword = create.password() != null && !create.password().isEmpty();
            byte[] salt = hasPassword ? randomBytes(16) : null;
            byte[] hash = hasPassword ? hashPassword(create.password(), salt) : null;
            Lobby lobby = new Lobby(create.name().strip(), conn, List.copyOf(create.deck()), salt, hash, now);
            lobbies.put(lobby.id, lobby);
            conn.lobby = lobby;
            send(conn, new LobbyServerMessage.LobbyJoined(view(lobby), true));
        }
    }

    private void join(Conn conn, JoinLobby join, long now) {
        Lobby lobby = join.lobbyId() == null ? null : lobbies.get(join.lobbyId());
        if (conn.lobby != null) {
            error(conn, Code.ALREADY_IN_LOBBY, "Leave your current lobby first");
        } else if (lobby == null) {
            error(conn, Code.LOBBY_NOT_FOUND, "No such lobby");
        } else if (lobby.state != LobbyState.OPEN) {
            error(conn, Code.LOBBY_STARTED, "That match has already started");
        } else if (lobby.guest != null) {
            error(conn, Code.LOBBY_FULL, "That lobby is full");
        } else if (!validDeck(join.deck())) {
            error(conn, Code.INVALID_DECK, "That deck is not valid");
        } else if (lobby.passwordProtected() && locked(conn, lobby, now)) {
            // refused without even looking at the password: a locked key gets no more guesses
        } else if (lobby.passwordProtected() && !passwordMatches(lobby, join.password())) {
            addressFailures.recordFailure(addressKey(conn), now);
            lobbyFailures.recordFailure("lobby:" + lobby.id, now);
            if (++conn.wrongPasswords >= MAX_PASSWORD_ATTEMPTS) {
                error(conn, Code.TOO_MANY_ATTEMPTS, "Too many wrong passwords");
                conn.link.close();
            } else {
                error(conn, Code.WRONG_PASSWORD, "Wrong password");
            }
        } else {
            if (lobby.passwordProtected()) {
                addressFailures.recordSuccess(addressKey(conn));
                lobbyFailures.recordSuccess("lobby:" + lobby.id);
            }
            lobby.guest = conn;
            lobby.guestDeck = List.copyOf(join.deck());
            lobby.lastActivityNanos = now;
            conn.lobby = lobby;
            send(conn, new LobbyServerMessage.LobbyJoined(view(lobby), false));
            send(lobby.host, new LobbyServerMessage.LobbyUpdate(view(lobby)));
        }
    }

    /** The host leaving closes the lobby; the guest leaving just frees the seat. */
    private void leave(Conn conn, String reasonForLeaver) {
        Lobby lobby = conn.lobby;
        if (lobby == null) {
            error(conn, Code.NOT_IN_LOBBY, "You are not in a lobby");
            return;
        }
        if (lobby.state != LobbyState.OPEN) {
            error(conn, Code.LOBBY_STARTED, "The match has started");
            return;
        }
        if (conn == lobby.host) {
            closeLobby(lobby, "The host left");
        } else {
            lobby.guest = null;
            lobby.guestDeck = null;
            conn.lobby = null;
            send(lobby.host, new LobbyServerMessage.LobbyUpdate(view(lobby)));
        }
    }

    private void start(Conn conn, long now) {
        Lobby lobby = conn.lobby;
        if (lobby == null) {
            error(conn, Code.NOT_IN_LOBBY, "You are not in a lobby");
        } else if (conn != lobby.host) {
            error(conn, Code.NOT_HOST, "Only the host can start the match");
        } else if (lobby.state != LobbyState.OPEN) {
            error(conn, Code.LOBBY_STARTED, "The match is already starting");
        } else if (lobby.guest == null) {
            error(conn, Code.NEED_TWO_PLAYERS, "Wait for another player");
        } else if (activeMatches() >= maxMatches) {
            error(conn, Code.SERVER_BUSY, "The server is running as many matches as it can, try again shortly");
        } else {
            lobby.state = LobbyState.STARTING;
            lobby.launchDeadlineNanos = now + LAUNCH_TIMEOUT_NANOS;
            try {
                lobby.launch = launcher.launch(new MatchLauncher.MatchSpec(lobby.hostDeck, lobby.guestDeck, RANDOM.nextLong()));
            } catch (RuntimeException e) {
                lobby.launch = CompletableFuture.failedFuture(e);
            }
        }
    }

    // --- launching and forwarding ---

    private void finishLaunches(long now) {
        for (Iterator<Lobby> it = lobbies.values().iterator(); it.hasNext(); ) {
            Lobby lobby = it.next();
            if (lobby.state != LobbyState.STARTING || lobby.launch == null) continue;
            if (!lobby.launch.isDone()) {
                if (now - lobby.launchDeadlineNanos >= 0) {
                    closeLateEndpoint(lobby);
                    launchFailed(lobby, "The match server did not start in time");
                }
                continue;
            }
            try {
                bridge(lobby, lobby.launch.get());
            } catch (Exception e) {
                Logger.error("LobbyServer", "Launching a match failed: " + e);
                launchFailed(lobby, "The match server could not be started");
            }
        }
    }

    /** A launch nobody waits for any more must not leave a match server running when it finishes. */
    private static void closeLateEndpoint(Lobby lobby) {
        if (lobby.launch == null) return;
        lobby.launch.whenComplete((endpoint, error) -> {
            if (endpoint != null) endpoint.close();
        });
    }

    private void bridge(Lobby lobby, MatchLauncher.MatchEndpoint endpoint) throws java.io.IOException {
        LineLink hostUp = endpoint.connect();
        LineLink guestUp;
        try {
            guestUp = endpoint.connect();
        } catch (java.io.IOException | RuntimeException e) {
            hostUp.close();
            endpoint.close();
            throw e;
        }
        lobby.endpoint = endpoint;
        lobby.state = LobbyState.IN_MATCH;
        lobby.slots[0].conn = lobby.host;
        lobby.slots[1].conn = lobby.guest;
        send(lobby.host, new LobbyServerMessage.MatchStarting("P1", lobby.slots[0].rejoinToken));
        send(lobby.guest, new LobbyServerMessage.MatchStarting("P2", lobby.slots[1].rejoinToken));
        lobby.host.upstream = hostUp;
        lobby.guest.upstream = guestUp;
    }

    private void launchFailed(Lobby lobby, String message) {
        lobby.state = LobbyState.OPEN;
        lobby.launch = null;
        lobby.lastActivityNanos = nanoClock.getAsLong();
        for (Conn member : lobby.members()) error(member, Code.START_FAILED, message);
    }

    /**
     * Copies what each side has said since the last tick to the other, unread — apart from two
     * things the lobby has to know: a player saying Leave (they are going for good), and the match
     * server saying MatchEnded (nothing left to hold). Both are recognised by the exact prefix the
     * codec writes, which a hostile client can't forge into anyone else's stream.
     */
    private void forward(Conn conn) {
        String line;
        int budget = 256;
        while (budget-- > 0 && (line = conn.link.pollLine()) != null) {
            if (line.startsWith(LEAVE_PREFIX)) conn.leftMatch = true;
            conn.upstream.send(line);
        }
        budget = 256;
        while (budget-- > 0 && (line = conn.upstream.pollLine()) != null) {
            if (line.startsWith(MATCH_ENDED_PREFIX) && conn.lobby != null) conn.lobby.matchEnded = true;
            conn.link.send(line);
        }
    }

    // --- leaving, closing, expiring ---

    private void disconnect(Conn conn) {
        conn.link.close();
        if (conn.upstream != null) conn.upstream.close();
        Lobby lobby = conn.lobby;
        if (lobby == null) return;
        if (lobby.state == LobbyState.OPEN) {
            conn.lobby = null;
            if (conn == lobby.host) closeLobby(lobby, "The host left");
            else {
                lobby.guest = null;
                lobby.guestDeck = null;
                send(lobby.host, new LobbyServerMessage.LobbyUpdate(view(lobby)));
            }
        } else if (lobby.state == LobbyState.STARTING) {
            conn.lobby = null;
            for (Conn member : lobby.members()) {
                if (member != conn && member.lobby == lobby) {
                    member.lobby = null;
                    send(member, new LobbyServerMessage.LobbyClosed("The other player left before the match started"));
                }
            }
            lobbies.remove(lobby.id);
            closeLateEndpoint(lobby);
        } else if (lobby.state == LobbyState.IN_MATCH) {
            conn.lobby = null;
            long now = nanoClock.getAsLong();
            for (SeatSlot slot : lobby.slots) {
                if (slot.conn == conn) {
                    slot.conn = null;
                    slot.vacantSince = now;
                }
                if (slot.pending == conn) slot.pending = null;
            }
            // A match that ended has nothing to wait for; otherwise a drop is held for REJOIN_HOLD_NANOS
            // (an explicit Leave was already settled by forfeit()) and the other player is told why it is quiet.
            if (lobby.matchEnded && lobby.slots[0].conn == null && lobby.slots[1].conn == null) {
                endMatch(lobby);
            } else if (!lobby.matchEnded) {
                for (SeatSlot slot : lobby.slots) {
                    if (slot.conn != null) tellOpponentConnection(slot.conn, false, REJOIN_HOLD_NANOS / SECOND);
                }
            }
        }
    }

    private void endMatch(Lobby lobby) {
        if (lobby.endpoint != null) lobby.endpoint.close();
        lobby.host.lobby = null;
        if (lobby.guest != null) lobby.guest.lobby = null;
        for (SeatSlot slot : lobby.slots) {
            if (slot.pending != null) {
                slot.pending.lobby = null;
                slot.pending.awaitingAttach = false;
                send(slot.pending, new LobbyServerMessage.LobbyError(Code.REJOIN_REFUSED, "The match is over"));
                slot.pending = null;
            }
        }
        lobbies.remove(lobby.id);
    }

    // --- running matches: forfeit, the rejoin hold, rejoining ---

    /** Tells a player they won (the other side is gone for good) in the match protocol's own words. */
    private void declareWinner(SeatSlot winner) {
        if (winner.conn == null) return;
        winner.conn.link.send(io.github.elderpath_crusade.net.protocol.MessageCodec.encode(
                new io.github.elderpath_crusade.net.protocol.ServerMessage.MatchEnded(
                        io.github.elderpath_crusade.enums.PieceAlignment.valueOf(winner.seatName))));
    }

    /** A player said Leave: the other wins at once and the match server is stopped. */
    private void forfeit(Lobby lobby, Conn leaver) {
        if (lobby.state != LobbyState.IN_MATCH) return;
        if (!lobby.matchEnded) {
            for (SeatSlot slot : lobby.slots) if (slot.conn != null && slot.conn != leaver) declareWinner(slot);
        }
        leaver.lobby = null;
        for (SeatSlot slot : lobby.slots) if (slot.conn == leaver) slot.conn = null;
        endMatch(lobby);
    }

    /** Hands over seats whose takeover delay is up, and ends matches whose dropped player stayed away too long. */
    private void runMatchUpkeep(long now) {
        for (Lobby lobby : new ArrayList<>(lobbies.values())) {
            if (lobby.state != LobbyState.IN_MATCH || lobby.matchEnded) continue;
            if (lobby.endpoint != null && !lobby.endpoint.isAlive()) {
                // The match server is gone (crashed or timed out): nothing left to hold for.
                endMatch(lobby);
                continue;
            }
            for (SeatSlot slot : lobby.slots) {
                if (slot.pending != null && now - slot.pendingAttachNanos >= 0) {
                    if (!attach(lobby, slot)) break;
                }
            }
            if (!lobbies.containsKey(lobby.id)) continue;
            for (int i = 0; i < 2; i++) {
                SeatSlot slot = lobby.slots[i];
                if (slot.conn == null && slot.pending == null && now - slot.vacantSince >= REJOIN_HOLD_NANOS) {
                    declareWinner(lobby.slots[1 - i]);
                    endMatch(lobby);
                    break;
                }
            }
        }
    }

    private void rejoin(Conn conn, RejoinMatch message, long now) {
        String key = addressKey(conn);
        if (conn.lobby != null) {
            error(conn, Code.ALREADY_IN_LOBBY, "Leave your current lobby first");
            return;
        }
        long wait = addressFailures.secondsLeft(key, now);
        if (wait > 0) {
            error(conn, Code.TOO_MANY_ATTEMPTS, "Too many failed attempts, try again in " + wait + " s");
            return;
        }
        Lobby lobby = message.lobbyId() == null ? null : lobbies.get(message.lobbyId());
        SeatSlot slot = null;
        if (lobby != null && lobby.state == LobbyState.IN_MATCH && !lobby.matchEnded && lobby.endpoint != null
                && message.rejoinToken() != null) {
            for (SeatSlot candidate : lobby.slots) {
                if (tokenMatches(candidate.rejoinToken, message.rejoinToken())) slot = candidate;
            }
        }
        if (slot == null) {
            addressFailures.recordFailure(key, now);
            error(conn, Code.REJOIN_REFUSED, "There is no match to rejoin");
            return;
        }
        addressFailures.recordSuccess(key);

        // The token holder may take the seat even while the old connection still looks alive —
        // a dead network often leaves a socket open for minutes. The old one is dropped, and the
        // new one is bridged a moment later so the match server has noticed the old one is gone.
        boolean takeover = slot.conn != null;
        if (slot.pending != null) {
            slot.pending.lobby = null;
            slot.pending.awaitingAttach = false;
            slot.pending.link.close();
        }
        if (takeover) {
            Conn old = slot.conn;
            slot.conn = null;
            old.lobby = null;
            old.link.close();
            if (old.upstream != null) old.upstream.close();
            slot.vacantSince = now;
        }
        slot.pending = conn;
        slot.pendingAttachNanos = takeover ? now + TAKEOVER_DELAY_NANOS : now;
        conn.lobby = lobby;
        conn.awaitingAttach = true;
        if (!takeover) attach(lobby, slot);
    }

    /** Opens the player's own connection to the match server and bridges it. Returns false if the match is gone. */
    private boolean attach(Lobby lobby, SeatSlot slot) {
        Conn conn = slot.pending;
        slot.pending = null;
        conn.awaitingAttach = false;
        LineLink upstream;
        try {
            upstream = lobby.endpoint.connect();
        } catch (java.io.IOException | RuntimeException e) {
            Logger.error("LobbyServer", "Could not reconnect a player to the match server: " + e);
            conn.lobby = null;
            error(conn, Code.REJOIN_REFUSED, "The match server is gone");
            SeatSlot other = lobby.slots[slot == lobby.slots[0] ? 1 : 0];
            declareWinner(other);
            endMatch(lobby);
            return false;
        }
        send(conn, new LobbyServerMessage.MatchStarting(slot.seatName, slot.rejoinToken));
        conn.upstream = upstream;
        conn.leftMatch = false;
        slot.conn = conn;
        // The player who stayed hears that they are back; the one who came back hears if the other is away too.
        SeatSlot other = slot == lobby.slots[0] ? lobby.slots[1] : lobby.slots[0];
        if (other.conn != null) tellOpponentConnection(other.conn, true, 0);
        else tellOpponentConnection(conn, false, Math.max(0, (REJOIN_HOLD_NANOS - (nanoClock.getAsLong() - other.vacantSince)) / SECOND));
        return true;
    }

    /** Tells a bridged player, in the match protocol's words, whether their opponent is connected. */
    private void tellOpponentConnection(Conn to, boolean connected, long holdSeconds) {
        to.link.send(io.github.elderpath_crusade.net.protocol.MessageCodec.encode(
                new io.github.elderpath_crusade.net.protocol.ServerMessage.OpponentConnection(connected, holdSeconds)));
    }

    private static boolean tokenMatches(String expected, String presented) {
        return MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                presented.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void closeLobby(Lobby lobby, String reason) {
        lobbies.remove(lobby.id);
        for (Conn member : lobby.members()) {
            if (member.lobby == lobby) {
                member.lobby = null;
                send(member, new LobbyServerMessage.LobbyClosed(reason));
            }
        }
    }

    private void expireLobbies(long now) {
        for (Lobby lobby : new ArrayList<>(lobbies.values())) {
            if (lobby.state == LobbyState.OPEN && now - lobby.lastActivityNanos >= LOBBY_IDLE_NANOS) {
                closeLobby(lobby, "The lobby timed out");
            }
        }
    }

    // --- helpers ---

    private List<LobbyServerMessage.LobbyView> openLobbyViews() {
        List<LobbyServerMessage.LobbyView> views = new ArrayList<>();
        for (Lobby lobby : lobbies.values()) {
            if (lobby.state != LobbyState.OPEN) continue;
            views.add(view(lobby));
            if (views.size() >= MAX_LISTED_LOBBIES) break;
        }
        return views;
    }

    private static LobbyServerMessage.LobbyView view(Lobby lobby) {
        List<String> names = new ArrayList<>();
        for (Conn member : lobby.members()) names.add(member.playerName);
        return new LobbyServerMessage.LobbyView(lobby.id, lobby.name, names, lobby.passwordProtected());
    }

    /** The key per-address limits use: the peer address, or — for links with none — the connection itself. */
    private static String addressKey(Conn conn) {
        String peer = conn.link.peerAddress();
        return peer != null ? "ip:" + peer : "conn:" + System.identityHashCode(conn);
    }

    /** Whether conn (or the lobby it wants) has used up its guesses; tells the player how long to wait. */
    private boolean locked(Conn conn, Lobby lobby, long now) {
        long wait = Math.max(addressFailures.secondsLeft(addressKey(conn), now),
                lobbyFailures.secondsLeft("lobby:" + lobby.id, now));
        if (wait <= 0) return false;
        error(conn, Code.TOO_MANY_ATTEMPTS, "Too many wrong passwords, try again in " + wait + " s");
        return true;
    }

    private boolean validDeck(List<String> deck) {
        if (deck == null || deck.isEmpty() || deck.size() > MAX_DECK_CARDS) return false;
        for (String card : deck) if (card == null || !knownCard.test(card)) return false;
        return true;
    }

    /** 1..max characters after trimming, no control characters. */
    private static boolean validText(String text, int max) {
        if (text == null) return false;
        String stripped = text.strip();
        if (stripped.isEmpty() || stripped.length() > max) return false;
        for (int i = 0; i < stripped.length(); i++) if (Character.isISOControl(stripped.charAt(i))) return false;
        return true;
    }

    private static boolean passwordMatches(Lobby lobby, String presented) {
        if (presented == null || presented.length() > MAX_PASSWORD) return false;
        return MessageDigest.isEqual(lobby.passwordHash, hashPassword(presented, lobby.salt));
    }

    private static byte[] hashPassword(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (java.security.NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String newToken() {
        return HexFormat.of().formatHex(randomBytes(16));
    }

    private static String newId() {
        return HexFormat.of().formatHex(randomBytes(4));
    }

    private static void send(Conn conn, LobbyServerMessage message) {
        conn.link.send(LobbyCodec.encode(message));
    }

    private static void error(Conn conn, Code code, String message) {
        send(conn, new LobbyServerMessage.LobbyError(code, message));
    }
}
