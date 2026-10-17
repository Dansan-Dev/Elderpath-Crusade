package io.github.elderpath_crusade.e2e;

import io.github.elderpath_crusade.client.MatchClient;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.net.protocol.MatchView;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import io.github.elderpath_crusade.server.MatchFixtures;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Builds a match on a ServerBackend, connects TestClients to it and drives it, checking the
 * end-to-end invariants after every server tick:
 * - every seated client's replica equals what the server says it may see (MatchView.diff) —
 *   or, for a backend whose server isn't in this JVM, the clients agree with each other
 * - no client ever receives the opponent's real card ids or drawn card names
 * - no command a driver sent is rejected (a driver only sends what it believes is legal)
 * - a bot seat never has a pick pending (bots answer synchronously — anything else stalls)
 * runToEnd additionally fails a match that stops making progress.
 */
public final class MatchHarness implements AutoCloseable {
    /** Simulated seconds per server tick — bots pace their actions on the match clock. */
    static final float DELTA = 0.1f;
    private static final long SETTLE_DEADLINE_MS = 10_000;
    /**
     * A match counts as stalled after this many steps AND this much real time without a new
     * turn — steps for in-JVM servers ticked here, time for a server that runs on its own clock.
     */
    private static final int STALL_TICKS = 3_000;
    private static final long STALL_NANOS = 30_000_000_000L;

    private final ServerBackend backend;
    private final List<TestClient> clients = new ArrayList<>();
    private int observedTurns;
    private PieceAlignment lastObservedPlayer;
    private boolean expectRejections;

    public MatchHarness(ServerBackend backend) {
        this.backend = backend;
    }

    public ServerBackend backend() {
        return backend;
    }

    public void start(MatchConfig config) {
        try {
            backend.start(config);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't start the " + backend.name() + " server", e);
        }
    }

    /** Connects a client asking for seat; its Welcome arrives on the next step(). driver may be null (scripted). */
    public TestClient join(SeatRequest seat, String name, ClientDriver driver) {
        List<String> raw = TestClient.newRawLog();
        try {
            TestClient client = new TestClient(name, new MatchClient(backend.connect(raw::add), seat, name), raw, driver);
            clients.add(client);
            return client;
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't connect " + name, e);
        }
    }

    /**
     * Joins a seat that was just left, retrying while the server still refuses it as taken — a
     * server on its own clock (child process) may not have processed the Leave yet, the same
     * race a reconnecting game client would retry through.
     */
    public TestClient rejoin(SeatRequest seat, String name, ClientDriver driver) {
        long deadline = System.nanoTime() + SETTLE_DEADLINE_MS * 1_000_000L;
        while (true) {
            TestClient client = join(seat, name, driver);
            step();
            ServerMessage.Error error = client.state().getLastError();
            if (client.state().isWelcomed() || error == null || error.code() != ServerMessage.Error.Code.SEAT_TAKEN) {
                return client;
            }
            clients.remove(client);
            client.client().close();
            if (System.nanoTime() - deadline > 0) fail(backend.name() + ": " + seat + " was never freed after its client left");
            pause();
        }
    }

    /** The client leaves (its seat stays reserved) and is no longer checked. */
    public void leave(TestClient client) {
        client.client().leave();
        clients.remove(client);
    }

    /** For scripted scenarios that send illegal commands on purpose. */
    public void expectRejections(boolean expect) {
        this.expectRejections = expect;
    }

    /**
     * Delivers everything sent so far. Synchronous backends: one tick, then every client polls.
     * Asynchronous ones: tick and poll until every client is seated, has no unacknowledged
     * command and agrees with the server, for two quiet rounds in a row.
     */
    public void step() {
        if (!backend.asynchronous()) {
            tickOnce();
            failOn(mismatches());
            return;
        }
        long begin = System.nanoTime();
        long deadline = begin + SETTLE_DEADLINE_MS * 1_000_000L;
        long quietNanos = backend.quietMillis() * 1_000_000L;
        int quietRounds = 0;
        long quietSince = 0;
        int rounds = 0;
        List<String> pending = List.of();
        while (true) {
            rounds++;
            int received = tickOnce();
            pending = mismatches();
            for (TestClient c : clients) {
                if (!c.client().isConnected()) continue; // e.g. a child server that exited after MatchEnded
                if (!c.state().isWelcomed()) pending.add(c + " isn't seated yet");
                if (c.client().hasUnackedCommands()) pending.add(c + " has unacknowledged commands");
            }
            if (pending.isEmpty() && received == 0) {
                if (quietRounds++ == 0) quietSince = System.nanoTime();
            } else {
                quietRounds = 0;
            }
            if (quietRounds >= 2 && System.nanoTime() - quietSince >= quietNanos) return;
            if (System.nanoTime() - deadline > 0) {
                fail(backend.name() + ": never settled after " + rounds + " rounds / "
                        + (System.nanoTime() - begin) / 1_000_000 + " ms (last round received " + received + "): " + pending);
            }
            pause();
        }
    }

    /**
     * Plays until the match ends or maxTurns turns have started, letting each client's driver
     * act (at most one command per step). Returns the winner, or empty at the turn cap.
     */
    public Optional<PieceAlignment> runToEnd(int maxTurns) {
        int ticksAtLastTurn = 0;
        long nanosAtLastTurn = System.nanoTime();
        int lastTurns = turns();
        int ticks = 0;
        while (!ended() && turns() < maxTurns) {
            step();
            ticks++;
            for (TestClient c : List.copyOf(clients)) {
                if (c.driver() != null && c.client().isConnected()) c.driver().act(c);
            }
            if (turns() != lastTurns) {
                lastTurns = turns();
                ticksAtLastTurn = ticks;
                nanosAtLastTurn = System.nanoTime();
            } else if (ticks - ticksAtLastTurn > STALL_TICKS && System.nanoTime() - nanosAtLastTurn > STALL_NANOS) {
                fail(backend.name() + ": the match stalled on turn " + turns());
            }
        }
        step(); // deliver the last command's results (and MatchEnded)
        return ended() ? Optional.ofNullable(winner()) : Optional.empty();
    }

    public int turns() {
        return backend.turnsStarted().orElse(observedTurns);
    }

    public boolean ended() {
        return backend.ended().orElseGet(() -> clients.stream().anyMatch(c -> c.state().isEnded()));
    }

    private PieceAlignment winner() {
        for (TestClient c : clients) if (c.state().isEnded()) return c.state().getWinner();
        return backend.winner().orElse(null);
    }

    /** One server tick, every client polls, the per-tick invariants. Returns how many messages arrived. */
    private int tickOnce() {
        backend.tick(DELTA);
        int received = 0;
        for (TestClient c : clients) received += c.client().poll();
        observeTurns();
        if (backend.botPickPending()) fail(backend.name() + ": a bot seat has a pick pending — it would stall");
        for (TestClient c : clients) {
            PieceAlignment seat = c.state().getSeat();
            if (seat != null) {
                MatchFixtures.assertNoForeignCardLeak(c.unscannedLines(), opponentOf(seat).name());
            }
            ServerMessage.Rejected rejected = c.newRejection();
            if (rejected != null && !expectRejections) {
                fail(c + "'s command " + rejected.seq() + " was rejected: " + rejected.reason());
            }
        }
        return received;
    }

    /** Replica disagreements right now (empty = consistent). */
    private List<String> mismatches() {
        List<String> out = new ArrayList<>();
        List<TestClient> seated = new ArrayList<>();
        for (TestClient c : clients) {
            if (c.state().isWelcomed() && c.client().isConnected()) seated.add(c);
        }
        for (TestClient c : seated) {
            PieceAlignment seat = c.state().getSeat();
            Optional<MatchView> truth = backend.authoritativeView(seat);
            if (truth.isPresent()) {
                for (String d : MatchView.diff(truth.get(), c.state().toView())) out.add(c + ": " + d);
            }
        }
        // Without the server's own state, the replicas' public parts must at least agree.
        if (backend.authoritativeView(PieceAlignment.P1).isEmpty() && seated.size() == 2) {
            MatchView a = seated.get(0).state().toView();
            MatchView b = seated.get(1).state().toView();
            if (!Objects.equals(a.pieces(), b.pieces())) out.add("pieces differ between clients");
            if (a.p1Mana() != b.p1Mana() || a.p2Mana() != b.p2Mana()) out.add("mana differs between clients");
            if (a.currentPlayer() != b.currentPlayer()) out.add("current player differs between clients");
            if (a.winner() != b.winner()) out.add("winner differs between clients");
        }
        return out;
    }

    /** Counts turns from the first client's replica, for backends that can't report them. */
    private void observeTurns() {
        if (clients.isEmpty()) return;
        TestClient c = clients.get(0);
        PieceAlignment current = c.state().isStarted() ? c.state().getCurrentPlayer() : null;
        if (current != null && current != lastObservedPlayer) {
            observedTurns++;
            lastObservedPlayer = current;
        }
    }

    private void failOn(List<String> mismatches) {
        if (!mismatches.isEmpty()) fail(backend.name() + ": replicas disagree with the server: " + mismatches);
    }

    private static PieceAlignment opponentOf(PieceAlignment side) {
        return side == PieceAlignment.P1 ? PieceAlignment.P2 : PieceAlignment.P1;
    }

    private static void pause() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            // JUnit's @Timeout interrupts the test thread — stop here instead of spinning on.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted (test timeout)", e);
        }
    }

    @Override
    public void close() {
        for (TestClient c : clients) c.client().close();
        clients.clear();
        backend.close();
    }
}
