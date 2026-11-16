package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.net.transport.InProcessLink;
import io.github.elderpath_crusade.net.transport.LineLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The reconnect loop against a real LobbyServer holding a running match, on a fake clock the sleeper advances. */
class MatchReconnectorTest {
    private static final long MS = 1_000_000L;
    private static final List<String> DECK = List.of("Wolf");

    private InProcessLobbyTransport transport;
    private LobbyServer server;
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);
    private final List<LineLink> matchSideLinks = new ArrayList<>();
    private boolean endpointAlive = true;

    private String lobbyId;
    private String guestToken;
    private LobbyClient host;
    private LobbyClient guest;
    private LineLink guestLink;

    @BeforeEach
    void setUp() {
        transport = new InProcessLobbyTransport();
        MatchLauncher launcher = spec -> CompletableFuture.completedFuture(new MatchLauncher.MatchEndpoint() {
            @Override public boolean isAlive() { return endpointAlive; }
            @Override public LineLink connect() {
                InProcessLink[] pair = InProcessLink.pair();
                matchSideLinks.add(pair[0]);
                return pair[1];
            }
            @Override public void close() { }
        });
        server = new LobbyServer(transport, launcher, 2, card -> card.equals("Wolf"));
        server.setNanoClock(clock::get);

        host = new LobbyClient(transport.connect(), "host");
        guestLink = transport.connect();
        guest = new LobbyClient(guestLink, "guest");
        tick();
        host.createLobby("R", null, DECK);
        tick();
        lobbyId = host.getLobby().id();
        guest.joinLobby(lobbyId, null, DECK);
        tick();
        tick();
        host.startMatch();
        tick();
        tick();
        guestToken = guest.getRejoinToken();
        assertNotNull(guestToken);
        guestLink.close(); // the guest's connection drops
        tick();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private void tick() {
        server.tick();
        host.poll();
        guest.poll();
    }

    private RejoinTicket ticket(String token) {
        return new RejoinTicket(new LobbyEndpoint("example.com", 443, true), lobbyId, token, "P2", "guest");
    }

    private LobbyConnection.Connector connector(List<Integer> failFirst) {
        int[] calls = {0};
        return (endpoint, name) -> {
            if (calls[0]++ < failFirst.size()) throw new IOException("no route");
            return new LobbyClient(transport.connect(), name);
        };
    }

    /** A sleeper that advances the fake clock and lets the lobby server run meanwhile. */
    private MatchReconnector.Sleeper sleeper(List<Long> slept) {
        return ms -> {
            slept.add(ms);
            clock.addAndGet(ms * MS);
            server.tick();
        };
    }

    private MatchReconnector reconnector(RejoinTicket ticket, LobbyConnection.Connector connector, List<Long> slept) {
        return new MatchReconnector(ticket, connector, clock::get, sleeper(slept), MatchReconnector.HOLD_NANOS, new Random(1));
    }

    @Test
    void rejoinsOnTheFirstTryWithTheTicketsToken() {
        List<Long> slept = new ArrayList<>();
        MatchReconnector r = reconnector(ticket(guestToken), connector(List.of()), slept);
        r.run();
        assertEquals(MatchReconnector.Phase.SUCCEEDED, r.getPhase());
        assertEquals(1, r.getAttempts());
        assertNotNull(r.takeConnection());
        assertNull(r.takeConnection(), "taken once");
        assertEquals(3, matchSideLinks.size(), "a new connection to the match server");
    }

    @Test
    void retriesWithGrowingBackoffUntilItConnects() {
        List<Long> slept = new ArrayList<>();
        MatchReconnector r = reconnector(ticket(guestToken), connector(List.of(1, 2)), slept);
        r.run();
        assertEquals(MatchReconnector.Phase.SUCCEEDED, r.getPhase());
        assertEquals(3, r.getAttempts());
        List<Long> backoffs = slept.stream().filter(ms -> ms >= 500).toList(); // the 20 ms polls are not backoffs
        assertEquals(2, backoffs.size());
        assertTrue(backoffs.get(0) >= 800 && backoffs.get(0) <= 1200, "first backoff ~1 s: " + backoffs.get(0));
        assertTrue(backoffs.get(1) >= 1600 && backoffs.get(1) <= 2400, "second backoff ~2 s: " + backoffs.get(1));
    }

    @Test
    void backoffIsCappedAtThirtySecondsPlusJitter() {
        MatchReconnector r = reconnector(ticket(guestToken), connector(List.of()), new ArrayList<>());
        for (int attempt = 1; attempt < 40; attempt++) {
            long ms = r.backoffMillis(attempt);
            assertTrue(ms >= 800, "attempt " + attempt);
            assertTrue(ms <= 36_000, "attempt " + attempt + " waited " + ms);
        }
        assertTrue(r.backoffMillis(30) >= 24_000, "late attempts wait about 30 s");
    }

    @Test
    void givesUpAtOnceWhenTheServerSaysThereIsNoSuchMatch() {
        MatchReconnector r = reconnector(ticket("0".repeat(32)), connector(List.of()), new ArrayList<>());
        r.run();
        assertEquals(MatchReconnector.Phase.GAVE_UP, r.getPhase());
        assertEquals(1, r.getAttempts());
        assertTrue(r.getMessage().contains("over"));
        assertNull(r.takeConnection());
    }

    @Test
    void givesUpWhenTheHoldTimeRunsOut_afterAboundedNumberOfAttempts() {
        List<Long> slept = new ArrayList<>();
        LobbyConnection.Connector alwaysFails = (endpoint, name) -> {
            throw new IOException("offline");
        };
        MatchReconnector r = reconnector(ticket(guestToken), alwaysFails, slept);
        r.run();
        assertEquals(MatchReconnector.Phase.GAVE_UP, r.getPhase());
        assertTrue(r.getAttempts() > 10 && r.getAttempts() < 80, "attempts over 20 minutes: " + r.getAttempts());
        assertEquals(0, r.getSecondsLeft());
        assertTrue(r.getMessage().contains("in time"));
    }

    @Test
    void closeStopsTrying() {
        MatchReconnector r = reconnector(ticket(guestToken), (endpoint, name) -> {
            throw new IOException("offline");
        }, new ArrayList<>());
        r.close();
        r.run();
        assertEquals(MatchReconnector.Phase.GAVE_UP, r.getPhase());
        assertEquals(0, r.getAttempts());
    }

    @Test
    void aMatchThatWasHeldTwentyMinutesIsGone() {
        clock.addAndGet(LobbyServer.REJOIN_HOLD_NANOS);
        server.tick();
        MatchReconnector r = reconnector(ticket(guestToken), connector(List.of()), new ArrayList<>());
        r.run();
        assertEquals(MatchReconnector.Phase.GAVE_UP, r.getPhase());
    }
}
