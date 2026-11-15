package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.AutoPlayer;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.net.protocol.ServerMessage;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.LongFunction;

/**
 * CLI-006: plays many fast games unattended — {@link AutoPlayer} acting for every seat this
 * session controls (solo: P1 vs the server's bot; hotseat: both seats) — and reports anything
 * abnormal. Each game gets its own seed (base + index), used for both the server's match and the
 * player's choices, so a failing game is replayed exactly with {@code --soak 1 --seed <its seed>}.
 *
 * <p>A game <b>fails</b> if any command is rejected (the player only ever sends what LegalActions
 * allow, so a rejection is a server/client disagreement), if nothing happens for {@link
 * #STALL_NANOS} (no command sent, no turn change), or if the session ends any way but ENDED
 * (server crashed/exited, disconnect). Hitting {@code maxTurns} is reported but isn't a failure —
 * two random players can wander forever.
 */
final class SoakRunner {
    static final int DEFAULT_MAX_TURNS = 200;
    static final long STALL_NANOS = 30_000_000_000L;
    static final long START_NANOS = 60_000_000_000L;
    /** The server-side bot is paced for human eyes by default; a soak run has no eyes. */
    static final String[] FAST_SERVER = {"--time-scale", "20", "--tick-ms", "5"};

    enum Outcome { WON, TURN_LIMIT, FAILED }

    record GameResult(int index, long seed, Outcome outcome, PieceAlignment winner, int turns, int commands,
                      int rejections, String failure, double seconds) {}

    record Summary(int games, int won, int turnLimited, int failed) {
        boolean ok() {
            return failed == 0;
        }
    }

    private final AutoPlayer.Strategy strategy;
    private final int maxTurns;
    private final PrintStream out;

    SoakRunner(AutoPlayer.Strategy strategy, int maxTurns, PrintStream out) {
        this.strategy = strategy;
        this.maxTurns = maxTurns;
        this.out = out;
    }

    /** Plays {@code games} games; {@code sessionForSeed} starts each one's session. */
    Summary run(int games, long baseSeed, LongFunction<ClientMatchSession> sessionForSeed) throws InterruptedException {
        int won = 0, limited = 0, failed = 0;
        for (int i = 0; i < games; i++) {
            long seed = baseSeed + i;
            GameResult result;
            try (ClientMatchSession session = sessionForSeed.apply(seed)) {
                result = play(i + 1, seed, session);
            } catch (RuntimeException e) {
                result = new GameResult(i + 1, seed, Outcome.FAILED, null, 0, 0, 0, "exception: " + e, 0);
            }
            out.println(describe(result));
            out.flush();
            switch (result.outcome()) {
                case WON -> won++;
                case TURN_LIMIT -> limited++;
                case FAILED -> failed++;
            }
        }
        Summary summary = new Summary(games, won, limited, failed);
        out.printf("soak: %d game(s) — %d finished, %d hit the turn limit, %d FAILED%n",
                summary.games(), summary.won(), summary.turnLimited(), summary.failed());
        return summary;
    }

    GameResult play(int index, long seed, ClientMatchSession session) throws InterruptedException {
        long start = System.nanoTime();
        AutoPlayer player = new AutoPlayer(strategy, new Random(seed));

        long deadline = start + START_NANOS;
        while (true) {
            session.poll();
            ClientMatchSession.Status status = session.getStatus();
            if (status.isReady()) break;
            if (status.isOver()) return failed(index, seed, start, 0, 0, 0, "never started: " + status);
            if (System.nanoTime() - deadline > 0) return failed(index, seed, start, 0, 0, 0, "never started: " + status);
            Thread.sleep(10);
        }

        int turns = 0, commands = 0, rejections = 0;
        String firstRejection = null;
        Map<PieceAlignment, Long> seenRejectionSeq = new HashMap<>();
        PieceAlignment lastPlayer = null;
        long lastProgress = System.nanoTime();
        while (true) {
            session.poll();
            ClientMatchSession.Status status = session.getStatus();

            for (PieceAlignment seat : session.getControlledSeats()) {
                ClientMatchState state = session.stateFor(seat);
                ServerMessage.Rejected r = state == null ? null : state.getLastRejection();
                if (r != null && !Long.valueOf(r.seq()).equals(seenRejectionSeq.get(seat))) {
                    seenRejectionSeq.put(seat, r.seq());
                    rejections++;
                    if (firstRejection == null) firstRejection = seat + " seq " + r.seq() + ": " + r.reason();
                }
            }

            if (status.isOver()) {
                if (status.phase() != ClientMatchSession.Phase.ENDED) {
                    return failed(index, seed, start, turns, commands, rejections, "session ended as " + status);
                }
                if (rejections > 0) return failed(index, seed, start, turns, commands, rejections, "rejected: " + firstRejection);
                return new GameResult(index, seed, Outcome.WON, session.getWinner(), turns, commands, 0, null, seconds(start));
            }

            PieceAlignment current = session.currentPlayer();
            if (current != null && current != lastPlayer) {
                lastPlayer = current;
                turns++;
                lastProgress = System.nanoTime();
                if (turns > maxTurns) {
                    if (rejections > 0) return failed(index, seed, start, turns, commands, rejections, "rejected: " + firstRejection);
                    return new GameResult(index, seed, Outcome.TURN_LIMIT, null, turns - 1, commands, 0, null, seconds(start));
                }
            }
            if (player.step(session)) {
                commands++;
                lastProgress = System.nanoTime();
            }
            if (System.nanoTime() - lastProgress > STALL_NANOS) {
                return failed(index, seed, start, turns, commands, rejections,
                        "stalled: nothing happened for " + STALL_NANOS / 1_000_000_000L + " s (current " + current
                                + ", unacked=" + session.hasUnackedCommands() + ")");
            }
            Thread.sleep(1);
        }
    }

    private static GameResult failed(int index, long seed, long start, int turns, int commands, int rejections, String why) {
        return new GameResult(index, seed, Outcome.FAILED, null, turns, commands, rejections, why, seconds(start));
    }

    private static double seconds(long start) {
        return (System.nanoTime() - start) / 1e9;
    }

    static String describe(GameResult r) {
        String head = String.format("game %d (seed %d): ", r.index(), r.seed());
        String tail = String.format(" — %d turns, %d commands, %.1f s", r.turns(), r.commands(), r.seconds());
        return switch (r.outcome()) {
            case WON -> head + (r.winner() == null ? "draw" : r.winner() + " won") + tail;
            case TURN_LIMIT -> head + "turn limit reached" + tail;
            case FAILED -> head + "FAILED: " + r.failure() + tail + " (replay: --soak 1 --seed " + r.seed() + ")";
        };
    }
}
