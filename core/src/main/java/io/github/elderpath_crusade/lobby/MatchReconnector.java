package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.lobby.LobbyServerMessage.LobbyError;
import io.github.elderpath_crusade.net.transport.ServerConnection;
import io.github.elderpath_crusade.utils.Logger;

import java.io.IOException;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * Gets a player back into a running lobby match after their connection dropped: dials the lobby
 * service again and sends RejoinMatch, retrying with exponential backoff and jitter (1 s, 2 s,
 * 4 s ... capped at 30 s) until it works, the service says the match is gone, or the hold time —
 * the same 20 minutes the lobby keeps the match for — runs out. Runs on its own thread; the screen
 * reads {@link #getPhase()} each frame and, on SUCCEEDED, takes the new match connection.
 */
public final class MatchReconnector implements AutoCloseable {
    public enum Phase { TRYING, SUCCEEDED, GAVE_UP }

    /** Pauses the retry thread — tests substitute one that drives a server instead of sleeping. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** How long the lobby holds a match for a dropped player. */
    public static final long HOLD_NANOS = 20L * 60 * 1_000_000_000L;

    private static final long STEP_TIMEOUT_NANOS = 10_000_000_000L;
    private static final long POLL_MILLIS = 20;
    private static final long BASE_BACKOFF_MILLIS = 1_000;
    private static final long MAX_BACKOFF_MILLIS = 30_000;

    private enum Outcome { SUCCESS, RETRY, FATAL }

    private final RejoinTicket ticket;
    private final LobbyConnection.Connector connector;
    private final LongSupplier clock;
    private final Sleeper sleeper;
    private final long holdNanos;
    private final Random random;

    private Phase phase = Phase.TRYING;
    private int attempts;
    private long deadlineNanos;
    private String message = "";
    private ServerConnection connection;
    private volatile boolean closed;

    public MatchReconnector(RejoinTicket ticket, LobbyConnection.Connector connector) {
        this(ticket, connector, System::nanoTime, Thread::sleep, HOLD_NANOS, new Random());
    }

    public MatchReconnector(RejoinTicket ticket, LobbyConnection.Connector connector, LongSupplier clock,
                            Sleeper sleeper, long holdNanos, Random random) {
        this.ticket = ticket;
        this.connector = connector;
        this.clock = clock;
        this.sleeper = sleeper;
        this.holdNanos = holdNanos;
        this.random = random;
    }

    /** Starts trying on a background thread. */
    public void start() {
        synchronized (this) {
            deadlineNanos = clock.getAsLong() + holdNanos;
        }
        Thread thread = new Thread(this::run, "match-reconnector");
        thread.setDaemon(true);
        thread.start();
    }

    /** The retry loop itself, callable directly from a test. */
    void run() {
        synchronized (this) {
            if (deadlineNanos == 0) deadlineNanos = clock.getAsLong() + holdNanos;
        }
        try {
            while (!closed) {
                if (clock.getAsLong() - deadlineNanos >= 0) {
                    finish(Phase.GAVE_UP, "Could not get back into the match in time");
                    return;
                }
                synchronized (this) {
                    attempts++;
                }
                Outcome outcome = attempt();
                if (outcome == Outcome.SUCCESS || outcome == Outcome.FATAL) return;
                sleeper.sleep(backoffMillis(attempts));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Outcome attempt() throws InterruptedException {
        LobbyClient client;
        try {
            client = connector.connect(ticket.endpoint(), ticket.playerName());
        } catch (IOException | RuntimeException e) {
            Logger.log("MatchReconnector", "attempt " + attempts + " could not connect: " + e);
            return Outcome.RETRY;
        }
        boolean keep = false;
        try {
            long until = clock.getAsLong() + STEP_TIMEOUT_NANOS;
            while (!closed && !client.isWelcomed() && client.isConnected() && clock.getAsLong() - until < 0) {
                client.poll();
                if (!client.isWelcomed()) sleeper.sleep(POLL_MILLIS);
            }
            if (!client.isWelcomed()) {
                LobbyError error = client.getLastError();
                if (error != null && error.code() == LobbyError.Code.VERSION_MISMATCH) {
                    finish(Phase.GAVE_UP, "This version of the game cannot reconnect to that server");
                    return Outcome.FATAL;
                }
                return Outcome.RETRY;
            }
            client.rejoinMatch(ticket.lobbyId(), ticket.rejoinToken());
            until = clock.getAsLong() + STEP_TIMEOUT_NANOS;
            while (!closed && client.getMatchSeat() == null && client.getLastError() == null && client.isConnected()
                    && clock.getAsLong() - until < 0) {
                client.poll();
                if (client.getMatchSeat() == null && client.getLastError() == null) sleeper.sleep(POLL_MILLIS);
            }
            if (client.getMatchSeat() != null) {
                ServerConnection taken = client.takeMatchConnection();
                keep = true;
                synchronized (this) {
                    if (closed) {
                        taken.close();
                        return Outcome.FATAL;
                    }
                    connection = taken;
                    phase = Phase.SUCCEEDED;
                    message = "Reconnected";
                }
                return Outcome.SUCCESS;
            }
            LobbyError error = client.getLastError();
            if (error != null && error.code() == LobbyError.Code.REJOIN_REFUSED) {
                finish(Phase.GAVE_UP, "The match is over");
                return Outcome.FATAL;
            }
            return Outcome.RETRY; // a locked-out address, a dropped link, a timeout: try again later
        } finally {
            if (!keep) client.close();
        }
    }

    /** Exponential backoff with +-20% jitter: 1 s, 2 s, 4 s ... up to 30 s. */
    long backoffMillis(int attempt) {
        long base = Math.min(MAX_BACKOFF_MILLIS, BASE_BACKOFF_MILLIS << Math.min(attempt - 1, 10));
        return (long) (base * (0.8 + 0.4 * random.nextDouble()));
    }

    private synchronized void finish(Phase result, String text) {
        phase = result;
        message = text;
    }

    public synchronized Phase getPhase() {
        return phase;
    }

    public synchronized int getAttempts() {
        return attempts;
    }

    /** Seconds left before giving up (0 once over). */
    public synchronized long getSecondsLeft() {
        return Math.max(0, (deadlineNanos - clock.getAsLong()) / 1_000_000_000L);
    }

    public synchronized String getMessage() {
        return message;
    }

    public RejoinTicket getTicket() {
        return ticket;
    }

    /** The new match connection once SUCCEEDED (null before, and after it was taken). */
    public synchronized ServerConnection takeConnection() {
        ServerConnection taken = connection;
        connection = null;
        return taken;
    }

    /** Stops trying (and drops a connection nobody took). */
    @Override
    public synchronized void close() {
        closed = true;
        if (connection != null) connection.close();
        connection = null;
        if (phase == Phase.TRYING) phase = Phase.GAVE_UP;
    }
}
