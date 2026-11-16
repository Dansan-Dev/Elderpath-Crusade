package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.lobby.LobbyClientMessage.*;
import io.github.elderpath_crusade.lobby.LobbyServerMessage.LobbyError.Code;
import io.github.elderpath_crusade.net.protocol.ProtocolException;
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

        Conn(LineLink link, long deadlineNanos) {
            this.link = link;
            this.deadlineNanos = deadlineNanos;
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
            } else {
                drainMessages(conn, now);
            }
            if (!conn.link.isOpen() || (conn.upstream != null && !conn.upstream.isOpen())) {
                disconnect(conn);
                it.remove();
            }
        }
        finishLaunches(now);
        expireLobbies(now);
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
        } else if (lobby.passwordProtected() && !passwordMatches(lobby, join.password())) {
            if (++conn.wrongPasswords >= MAX_PASSWORD_ATTEMPTS) {
                error(conn, Code.TOO_MANY_ATTEMPTS, "Too many wrong passwords");
                conn.link.close();
            } else {
                error(conn, Code.WRONG_PASSWORD, "Wrong password");
            }
        } else {
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
        send(lobby.host, new LobbyServerMessage.MatchStarting("P1"));
        send(lobby.guest, new LobbyServerMessage.MatchStarting("P2"));
        lobby.host.upstream = hostUp;
        lobby.guest.upstream = guestUp;
    }

    private void launchFailed(Lobby lobby, String message) {
        lobby.state = LobbyState.OPEN;
        lobby.launch = null;
        lobby.lastActivityNanos = nanoClock.getAsLong();
        for (Conn member : lobby.members()) error(member, Code.START_FAILED, message);
    }

    /** Copies what each side has said since the last tick to the other, unread. */
    private void forward(Conn conn) {
        String line;
        int budget = 256;
        while (budget-- > 0 && (line = conn.link.pollLine()) != null) conn.upstream.send(line);
        budget = 256;
        while (budget-- > 0 && (line = conn.upstream.pollLine()) != null) conn.link.send(line);
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
        } else {
            conn.lobby = null;
            Conn other = conn == lobby.host ? lobby.guest : lobby.host;
            if (other == null || other.lobby != lobby || !other.link.isOpen()) endMatch(lobby);
        }
    }

    private void endMatch(Lobby lobby) {
        if (lobby.endpoint != null) lobby.endpoint.close();
        lobby.host.lobby = null;
        if (lobby.guest != null) lobby.guest.lobby = null;
        lobbies.remove(lobby.id);
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
