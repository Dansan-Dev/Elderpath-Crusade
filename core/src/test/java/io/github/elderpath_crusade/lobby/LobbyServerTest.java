package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.lobby.LobbyClientMessage.*;
import io.github.elderpath_crusade.lobby.LobbyServerMessage.*;
import io.github.elderpath_crusade.net.transport.InProcessLink;
import io.github.elderpath_crusade.net.transport.LineLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LobbyServerTest {
    private static final List<String> DECK = List.of("Wolf", "Wolf Cub");

    private InProcessLobbyTransport transport;
    private FakeLauncher launcher;
    private LobbyServer server;
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);

    /** A launcher whose "match servers" are in-process link pairs the test can talk through. */
    private static final class FakeLauncher implements MatchLauncher {
        CompletableFuture<MatchEndpoint> next = null; // null: complete at once
        final List<MatchSpec> specs = new ArrayList<>();
        final List<LineLink> matchSideLinks = new ArrayList<>();
        int closed;

        @Override
        public CompletableFuture<MatchEndpoint> launch(MatchSpec spec) {
            specs.add(spec);
            CompletableFuture<MatchEndpoint> future = next != null ? next : CompletableFuture.completedFuture(endpoint());
            return future;
        }

        MatchEndpoint endpoint() {
            return new MatchEndpoint() {
                @Override
                public LineLink connect() {
                    InProcessLink[] pair = InProcessLink.pair();
                    matchSideLinks.add(pair[0]);
                    return pair[1];
                }

                @Override
                public void close() {
                    closed++;
                }
            };
        }
    }

    private final class Client {
        final LineLink link;

        Client() {
            this(transport.connect());
        }

        Client(LineLink link) {
            this.link = link;
        }
        final List<LobbyServerMessage> received = new ArrayList<>();
        final List<String> rawLines = new ArrayList<>();

        void send(LobbyClientMessage message) {
            link.send(LobbyCodec.encode(message));
        }

        void sendRaw(String line) {
            link.send(line);
        }

        /** Ticks the server, then reads everything sent so far. */
        List<LobbyServerMessage> poll() {
            server.tick();
            List<LobbyServerMessage> fresh = new ArrayList<>();
            String line;
            while ((line = link.pollLine()) != null) {
                rawLines.add(line);
                LobbyServerMessage m = LobbyCodec.decodeServer(line);
                received.add(m);
                fresh.add(m);
            }
            return fresh;
        }

        LobbyServerMessage last() {
            return received.get(received.size() - 1);
        }

        LobbyError lastError() {
            for (int i = received.size() - 1; i >= 0; i--) if (received.get(i) instanceof LobbyError e) return e;
            return null;
        }

        <T extends LobbyServerMessage> T lastOf(Class<T> type) {
            for (int i = received.size() - 1; i >= 0; i--) if (type.isInstance(received.get(i))) return type.cast(received.get(i));
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        transport = new InProcessLobbyTransport();
        launcher = new FakeLauncher();
        server = new LobbyServer(transport, launcher, 2, card -> Set.of("Wolf", "Wolf Cub", "Crow").contains(card));
        server.setNanoClock(clock::get);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private Client hello(String name) {
        Client c = new Client();
        c.send(new LobbyHello(LobbyCodec.LOBBY_PROTOCOL_VERSION, name));
        c.poll();
        assertInstanceOf(LobbyWelcome.class, c.last());
        return c;
    }

    private String create(Client host, String name, String password) {
        host.send(new CreateLobby(name, password, DECK));
        host.poll();
        return host.lastOf(LobbyJoined.class).lobby().id();
    }

    private Client joinedGuest(String lobbyId, String password) {
        Client guest = hello("guest");
        guest.send(new JoinLobby(lobbyId, password, DECK));
        guest.poll();
        return guest;
    }

    // --- hello ---

    @Test
    void helloIsRequiredBeforeAnythingElse() {
        Client c = new Client();
        c.send(new ListLobbies());
        c.poll();
        assertEquals(LobbyError.Code.NOT_HELLOED, c.lastError().code());
    }

    @Test
    void helloWithWrongVersionOrBadNameIsRefusedAndClosed() {
        Client old = new Client();
        old.send(new LobbyHello(99, "ann"));
        old.poll();
        assertEquals(LobbyError.Code.VERSION_MISMATCH, old.lastError().code());
        assertFalse(old.link.isOpen());

        for (String bad : new String[]{"", "   ", "x".repeat(LobbyServer.MAX_PLAYER_NAME + 1), "a\nb", null}) {
            Client c = new Client();
            c.send(new LobbyHello(LobbyCodec.LOBBY_PROTOCOL_VERSION, bad));
            c.poll();
            assertEquals(LobbyError.Code.INVALID_NAME, c.lastError().code(), String.valueOf(bad));
            assertFalse(c.link.isOpen());
        }
    }

    @Test
    void aConnectionThatNeverSaysHelloIsDroppedAfterTheDeadline() {
        Client silent = new Client();
        silent.poll();
        assertTrue(silent.link.isOpen());
        clock.addAndGet(LobbyServer.HELLO_TIMEOUT_NANOS);
        silent.poll();
        assertEquals(LobbyError.Code.NOT_HELLOED, silent.lastError().code());
        assertFalse(silent.link.isOpen());
    }

    // --- create / list / join ---

    @Test
    void aCreatedLobbyIsListedWithoutItsPassword() {
        Client host = hello("host");
        create(host, "Casual", "secret");
        Client browser = hello("browser");
        browser.send(new ListLobbies());
        browser.poll();
        LobbyList list = browser.lastOf(LobbyList.class);
        assertEquals(1, list.lobbies().size());
        assertEquals("Casual", list.lobbies().get(0).name());
        assertEquals(List.of("host"), list.lobbies().get(0).members());
        assertTrue(list.lobbies().get(0).passwordProtected());
        for (String line : browser.rawLines) assertFalse(line.contains("secret"));
    }

    @Test
    void joiningAnOpenLobby_tellsBothSides() {
        Client host = hello("host");
        String id = create(host, "Open", null);
        Client guest = joinedGuest(id, null);
        assertFalse(guest.lastOf(LobbyJoined.class).host());
        assertEquals(List.of("host", "guest"), guest.lastOf(LobbyJoined.class).lobby().members());
        host.poll();
        assertEquals(List.of("host", "guest"), host.lastOf(LobbyUpdate.class).lobby().members());
    }

    @Test
    void emptyPasswordMeansNoPassword() {
        Client host = hello("host");
        String id = create(host, "Open", "");
        assertNotNull(joinedGuest(id, null).lastOf(LobbyJoined.class));
    }

    @Test
    void aPasswordProtectedLobbyNeedsTheRightPassword() {
        Client host = hello("host");
        String id = create(host, "Private", "hunter2");

        assertEquals(LobbyError.Code.WRONG_PASSWORD, joinedGuest(id, null).lastError().code());
        assertEquals(LobbyError.Code.WRONG_PASSWORD, joinedGuest(id, "hunter3").lastError().code());
        assertEquals(LobbyError.Code.WRONG_PASSWORD, joinedGuest(id, "").lastError().code());
        Client good = joinedGuest(id, "hunter2");
        assertNotNull(good.lastOf(LobbyJoined.class));
    }

    @Test
    void repeatedWrongPasswordsDisconnectTheGuesser() {
        Client host = hello("host");
        String id = create(host, "Private", "hunter2");
        Client guesser = hello("guesser");
        for (int i = 0; i < LobbyServer.MAX_PASSWORD_ATTEMPTS; i++) {
            guesser.send(new JoinLobby(id, "guess" + i, DECK));
            guesser.poll();
        }
        assertEquals(LobbyError.Code.TOO_MANY_ATTEMPTS, guesser.lastError().code());
        assertFalse(guesser.link.isOpen());
    }

    @Test
    void aFullOrMissingOrStartedLobbyCannotBeJoined() {
        Client host = hello("host");
        String id = create(host, "Open", null);
        joinedGuest(id, null);
        assertEquals(LobbyError.Code.LOBBY_FULL, joinedGuest(id, null).lastError().code());
        assertEquals(LobbyError.Code.LOBBY_NOT_FOUND, joinedGuest("nope", null).lastError().code());
        Client nullId = hello("n");
        nullId.send(new JoinLobby(null, null, DECK));
        nullId.poll();
        assertEquals(LobbyError.Code.LOBBY_NOT_FOUND, nullId.lastError().code());
    }

    @Test
    void invalidDecksAreRefused() {
        Client host = hello("host");
        for (List<String> bad : List.of(List.<String>of(), List.of("Not A Card"), List.of("Wolf#run-0"),
                java.util.Collections.nCopies(LobbyServer.MAX_DECK_CARDS + 1, "Wolf"))) {
            host.send(new CreateLobby("L", null, bad));
            host.poll();
            assertEquals(LobbyError.Code.INVALID_DECK, host.lastError().code(), bad.toString());
        }
        host.send(new CreateLobby("L", null, null));
        host.poll();
        assertEquals(LobbyError.Code.INVALID_DECK, host.lastError().code());
        assertEquals(0, server.lobbyCount());
    }

    @Test
    void invalidLobbyNamesAreRefused() {
        Client host = hello("host");
        for (String bad : new String[]{"", " ", "x".repeat(LobbyServer.MAX_LOBBY_NAME + 1), "bad\tname", null}) {
            host.send(new CreateLobby(bad, null, DECK));
            host.poll();
            assertEquals(LobbyError.Code.INVALID_NAME, host.lastError().code(), String.valueOf(bad));
        }
        host.send(new CreateLobby("ok", "p".repeat(LobbyServer.MAX_PASSWORD + 1), DECK));
        host.poll();
        assertEquals(LobbyError.Code.INVALID_NAME, host.lastError().code());
        assertEquals(0, server.lobbyCount());
    }

    @Test
    void aPlayerCanBeInOnlyOneLobby() {
        Client host = hello("host");
        create(host, "One", null);
        host.send(new CreateLobby("Two", null, DECK));
        host.poll();
        assertEquals(LobbyError.Code.ALREADY_IN_LOBBY, host.lastError().code());
        assertEquals(1, server.lobbyCount());
    }

    @Test
    void theNumberOfLobbiesIsCapped() {
        for (int i = 0; i < LobbyServer.MAX_LOBBIES; i++) create(hello("h" + i), "L" + i, null);
        Client extra = hello("extra");
        extra.send(new CreateLobby("one too many", null, DECK));
        extra.poll();
        assertEquals(LobbyError.Code.TOO_MANY_LOBBIES, extra.lastError().code());
    }

    // --- leaving ---

    @Test
    void theHostLeavingClosesTheLobbyAndTellsTheGuest() {
        Client host = hello("host");
        String id = create(host, "Open", null);
        Client guest = joinedGuest(id, null);
        host.send(new LeaveLobby());
        host.poll();
        guest.poll();
        assertInstanceOf(LobbyClosed.class, guest.last());
        assertEquals(0, server.lobbyCount());
    }

    @Test
    void theGuestLeavingFreesTheSeat() {
        Client host = hello("host");
        String id = create(host, "Open", null);
        Client guest = joinedGuest(id, null);
        guest.send(new LeaveLobby());
        guest.poll();
        host.poll();
        assertEquals(List.of("host"), host.lastOf(LobbyUpdate.class).lobby().members());
        assertNotNull(joinedGuest(id, null).lastOf(LobbyJoined.class));
    }

    @Test
    void aDisconnectLeavesLikeALeave() {
        Client host = hello("host");
        String id = create(host, "Open", null);
        Client guest = joinedGuest(id, null);
        guest.link.close();
        host.poll();
        assertEquals(List.of("host"), host.lastOf(LobbyUpdate.class).lobby().members());
        host.link.close();
        server.tick();
        assertEquals(0, server.lobbyCount());
    }

    @Test
    void anIdleLobbyTimesOut() {
        Client host = hello("host");
        create(host, "Idle", null);
        clock.addAndGet(LobbyServer.LOBBY_IDLE_NANOS);
        host.poll();
        assertInstanceOf(LobbyClosed.class, host.last());
        assertEquals(0, server.lobbyCount());
    }

    // --- starting ---

    @Test
    void onlyTheHostWithAGuestCanStart() {
        Client host = hello("host");
        String id = create(host, "Open", null);
        host.send(new StartMatch());
        host.poll();
        assertEquals(LobbyError.Code.NEED_TWO_PLAYERS, host.lastError().code());
        Client guest = joinedGuest(id, null);
        guest.send(new StartMatch());
        guest.poll();
        assertEquals(LobbyError.Code.NOT_HOST, guest.lastError().code());
        Client outsider = hello("outsider");
        outsider.send(new StartMatch());
        outsider.poll();
        assertEquals(LobbyError.Code.NOT_IN_LOBBY, outsider.lastError().code());
        assertTrue(launcher.specs.isEmpty());
    }

    @Test
    void startLaunchesAMatchWithBothDecksAndBridgesBothPlayers() {
        Client host = hello("host");
        String id = create(host, "Open", null);
        Client guest = joinedGuest(id, null);
        host.send(new StartMatch());
        host.poll();
        guest.poll();

        assertEquals(1, launcher.specs.size());
        assertEquals(DECK, launcher.specs.get(0).p1Deck());
        assertEquals(DECK, launcher.specs.get(0).p2Deck());
        assertEquals("P1", host.lastOf(MatchStarting.class).seat());
        assertEquals("P2", guest.lastOf(MatchStarting.class).seat());
        assertEquals(2, launcher.matchSideLinks.size());

        // the bridge carries match lines both ways, unread
        host.send(new LeaveLobby()); // not a lobby message any more — must be forwarded verbatim
        host.sendRaw("{\"type\":\"Hello\",\"payload\":{}}");
        server.tick();
        LineLink hostMatchSide = launcher.matchSideLinks.get(0);
        String first = hostMatchSide.pollLine();
        assertEquals(LobbyCodec.encode(new LeaveLobby()), first);
        assertEquals("{\"type\":\"Hello\",\"payload\":{}}", hostMatchSide.pollLine());

        hostMatchSide.send("from-match-server");
        server.tick();
        assertEquals("from-match-server", host.link.pollLine());
        assertNull(guest.link.pollLine(), "the guest only hears its own match connection");
    }

    @Test
    void aStartedLobbyIsNoLongerListedOrJoinable() {
        Client host = hello("host");
        String id = create(host, "Open", null);
        joinedGuest(id, null);
        host.send(new StartMatch());
        host.poll();
        Client browser = hello("browser");
        browser.send(new ListLobbies());
        browser.poll();
        assertTrue(browser.lastOf(LobbyList.class).lobbies().isEmpty());
        browser.send(new JoinLobby(id, null, DECK));
        browser.poll();
        assertEquals(LobbyError.Code.LOBBY_STARTED, browser.lastError().code());
    }

    @Test
    void theMatchLimitRefusesStartsUntilAMatchEnds() {
        List<Client> hosts = new ArrayList<>();
        List<Client> guests = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Client h = hello("h" + i);
            String id = create(h, "L" + i, null);
            guests.add(joinedGuest(id, null));
            hosts.add(h);
        }
        for (int i = 0; i < 2; i++) {
            hosts.get(i).send(new StartMatch());
            hosts.get(i).poll();
        }
        assertEquals(2, server.activeMatches());
        hosts.get(2).send(new StartMatch());
        hosts.get(2).poll();
        assertEquals(LobbyError.Code.SERVER_BUSY, hosts.get(2).lastError().code());

        // match 0 ends: its match server closes both connections
        launcher.matchSideLinks.get(0).close();
        launcher.matchSideLinks.get(1).close();
        server.tick();
        server.tick();
        assertEquals(1, server.activeMatches());
        assertEquals(1, launcher.closed, "the ended match's server is stopped");
        hosts.get(2).send(new StartMatch());
        hosts.get(2).poll();
        assertInstanceOf(MatchStarting.class, hosts.get(2).last());
    }

    @Test
    void aFailedLaunchGoesBackToTheLobbyWithAnError() {
        launcher.next = CompletableFuture.failedFuture(new IOException("no java"));
        Client host = hello("host");
        String id = create(host, "Open", null);
        Client guest = joinedGuest(id, null);
        host.send(new StartMatch());
        host.poll();
        guest.poll();
        assertEquals(LobbyError.Code.START_FAILED, host.lastError().code());
        assertEquals(LobbyError.Code.START_FAILED, guest.lastError().code());
        assertEquals(0, server.activeMatches());
        launcher.next = null;
        host.send(new StartMatch());
        host.poll();
        assertInstanceOf(MatchStarting.class, host.last());
    }

    @Test
    void aSlowLaunchTimesOutAndALateMatchServerIsStopped() {
        CompletableFuture<MatchLauncher.MatchEndpoint> slow = new CompletableFuture<>();
        launcher.next = slow;
        Client host = hello("host");
        String id = create(host, "Open", null);
        Client guest = joinedGuest(id, null);
        host.send(new StartMatch());
        host.poll();
        assertEquals(1, server.activeMatches());
        clock.addAndGet(LobbyServer.LAUNCH_TIMEOUT_NANOS);
        host.poll();
        assertEquals(LobbyError.Code.START_FAILED, host.lastError().code());
        slow.complete(launcher.endpoint());
        assertEquals(1, launcher.closed, "a match server that finishes starting too late is stopped");
        assertEquals(0, server.activeMatches());
        assertNotNull(guest);
    }

    @Test
    void aPlayerLeavingWhileTheMatchIsStarting_cancelsItAndStopsTheLateServer() {
        CompletableFuture<MatchLauncher.MatchEndpoint> slow = new CompletableFuture<>();
        launcher.next = slow;
        Client host = hello("host");
        String id = create(host, "Open", null);
        Client guest = joinedGuest(id, null);
        host.send(new StartMatch());
        host.poll();
        guest.link.close();
        host.poll();
        assertInstanceOf(LobbyClosed.class, host.last());
        slow.complete(launcher.endpoint());
        assertEquals(1, launcher.closed);
        assertEquals(0, server.lobbyCount());
    }

    // --- hostile clients ---

    @Test
    void garbageGetsAnErrorEachThenADisconnect() {
        Client c = hello("garbage");
        for (int i = 0; i < LobbyServer.MAX_BAD_MESSAGES; i++) c.sendRaw("{garbage " + i);
        c.poll();
        long errors = c.received.stream().filter(m -> m instanceof LobbyError e && e.code() == LobbyError.Code.BAD_MESSAGE).count();
        assertEquals(LobbyServer.MAX_BAD_MESSAGES, errors);
        assertFalse(c.link.isOpen());
    }

    @Test
    void aFloodIsHandledAtAFixedRatePerTick() {
        Client c = hello("flood");
        for (int i = 0; i < 1_000; i++) c.send(new ListLobbies());
        c.poll();
        long lists = c.received.stream().filter(m -> m instanceof LobbyList).count();
        assertEquals(LobbyServer.MAX_MESSAGES_PER_TICK, lists);
    }

    @Test
    void helloTwiceIsAnError() {
        Client c = hello("twice");
        c.send(new LobbyHello(LobbyCodec.LOBBY_PROTOCOL_VERSION, "again"));
        c.poll();
        assertEquals(LobbyError.Code.BAD_MESSAGE, c.lastError().code());
    }

    @Test
    void oneLobbysPlayerCannotReadAnotherLobbysTraffic_andNamesAreFilteredOfControlCharacters() {
        Client a = hello("a");
        Client b = hello("b");
        create(a, "A-lobby", "pw-a");
        create(b, "B-lobby", null);
        a.poll();
        for (String line : b.rawLines) assertFalse(line.contains("pw-a"));
        Client sneaky = new Client();
        sneaky.send(new LobbyHello(LobbyCodec.LOBBY_PROTOCOL_VERSION, "bad\u0007name"));
        sneaky.poll();
        assertEquals(LobbyError.Code.INVALID_NAME, sneaky.lastError().code());
    }

    // --- guessing limits beyond a single connection ---

    private Client helloFrom(String name, String address) {
        Client c = new Client(transport.connect(address));
        c.send(new LobbyHello(LobbyCodec.LOBBY_PROTOCOL_VERSION, name));
        c.poll();
        return c;
    }

    @Test
    void aLobbyIsLockedAfterRepeatedWrongPasswords_evenAcrossConnections_andTheRightPasswordWaits() {
        Client host = hello("host");
        String id = create(host, "Private", "hunter2");
        for (int i = 0; i < 5; i++) {
            Client guesser = helloFrom("g" + i, "10.0.0." + i); // five different addresses, one connection each
            guesser.send(new JoinLobby(id, "guess" + i, DECK));
            guesser.poll();
            assertEquals(LobbyError.Code.WRONG_PASSWORD, guesser.lastError().code());
        }
        Client rightPassword = helloFrom("friend", "10.0.0.50");
        rightPassword.send(new JoinLobby(id, "hunter2", DECK));
        rightPassword.poll();
        assertEquals(LobbyError.Code.TOO_MANY_ATTEMPTS, rightPassword.lastError().code(), "locked: even the right password is refused for now");
        assertNull(rightPassword.lastOf(LobbyJoined.class));

        clock.addAndGet(61_000_000_000L);
        rightPassword.send(new JoinLobby(id, "hunter2", DECK));
        rightPassword.poll();
        assertNotNull(rightPassword.lastOf(LobbyJoined.class), "the lock ended");
    }

    @Test
    void anAddressThatKeepsGuessingAcrossLobbiesAndConnectionsIsLockedOut() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) ids.add(create(hello("h" + i), "L" + i, "pw" + i));
        int attempts = 0;
        LobbyError last = null;
        for (int round = 0; round < 12 && (last == null || last.code() != LobbyError.Code.TOO_MANY_ATTEMPTS); round++) {
            Client guesser = helloFrom("g" + round, "203.0.113.9"); // a fresh connection every time, same address
            guesser.send(new JoinLobby(ids.get(round % 4), "wrong", DECK));
            guesser.poll();
            last = guesser.lastError();
            attempts++;
        }
        assertNotNull(last);
        assertEquals(LobbyError.Code.TOO_MANY_ATTEMPTS, last.code());
        assertTrue(attempts <= 11, "stopped after about ten failures, took " + attempts);

        // another address is unaffected (the per-lobby limit allows 5 failures, the last lobby had ~2)
        Client other = helloFrom("other", "198.51.100.7");
        other.send(new JoinLobby(ids.get(3), "pw3", DECK));
        other.poll();
        assertNotNull(other.lastOf(LobbyJoined.class));
    }

    @Test
    void aCorrectPasswordClearsTheFailuresOfThatLobbyAndAddress() {
        Client host = hello("host");
        String id = create(host, "Private", "hunter2");
        for (int i = 0; i < 4; i++) {
            Client guesser = helloFrom("g" + i, "10.1.1.1");
            guesser.send(new JoinLobby(id, "no" + i, DECK));
            guesser.poll();
        }
        Client good = helloFrom("good", "10.1.1.2");
        good.send(new JoinLobby(id, "hunter2", DECK));
        good.poll();
        assertNotNull(good.lastOf(LobbyJoined.class));
        good.send(new LeaveLobby());
        good.poll();
        Client bad = helloFrom("bad", "10.1.1.3");
        bad.send(new JoinLobby(id, "nope", DECK));
        bad.poll();
        assertEquals(LobbyError.Code.WRONG_PASSWORD, bad.lastError().code(), "the count started over after the success");
    }
}
