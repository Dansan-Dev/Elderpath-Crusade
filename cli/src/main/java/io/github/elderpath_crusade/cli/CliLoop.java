package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.ClientCommands;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.net.protocol.MatchView;

import java.io.PrintStream;
import java.util.Objects;

/**
 * The render/read/dispatch loop from the plan: poll the session, print the board/status/hands
 * when they've changed, print a pick prompt when a new one arrives, read one command line
 * (blocking on stdin for interactive input, or the next script line), parse and dispatch it.
 * Takes a {@link LineSource} and a {@link PrintStream} so it's fully testable against a real
 * session without a real terminal — see {@code CliLoopTest}.
 */
final class CliLoop {
    /** How long one nextLine() wait may block before polling the session again — keeps opponent/bot moves visible between the human's own turns, per the plan. */
    private static final long POLL_TIMEOUT_MILLIS = 200;

    private CliLoop() {}

    static void run(ClientMatchSession session, LineSource lineSource, PrintStream out) throws InterruptedException {
        MatchView lastRenderedView = null;
        Long lastPromptedPickId = null;
        PieceAlignment lastActingSeat = null;
        boolean promptPending = false;

        while (true) {
            session.poll();
            ClientMatchSession.Status status = session.getStatus();
            if (status.isOver()) {
                out.println("session ended: " + status.phase()
                        + (status.detail() == null || status.detail().isEmpty() ? "" : " (" + status.detail() + ")"));
                return;
            }

            // Hotseat plays both seats through one session, switching which connection's state
            // viewState() shows as the turn passes — announce it so whoever's at the keyboard
            // knows control just passed to them. Silent going the other way (our seat loses the
            // turn, e.g. to a bot or the other side) — nothing for this screen to announce there.
            PieceAlignment acting = session.actingSeat();
            if (!Objects.equals(acting, lastActingSeat)) {
                if (acting != null) out.println("=== " + acting + "'s turn ===");
                lastActingSeat = acting;
            }

            ClientMatchState state = session.viewState();
            if (state != null) {
                MatchView view = state.toView();
                if (!Objects.equals(view, lastRenderedView)) {
                    out.println(StateRenderer.renderBoard(state));
                    out.println(StateRenderer.renderStatus(state));
                    out.println(StateRenderer.renderHands(state));
                    lastRenderedView = view;
                }
                PickRequest pick = state.getPendingPick();
                Long pickId = pick == null ? null : pick.pickId();
                if (!Objects.equals(pickId, lastPromptedPickId)) {
                    if (pick != null) out.println(StateRenderer.renderPick(pick));
                    lastPromptedPickId = pickId;
                }
            }

            if (lineSource.exhausted()) return;

            if (lineSource.interactive() && !promptPending) {
                out.print("> ");
                out.flush();
                promptPending = true;
            }
            String line = lineSource.nextLine(POLL_TIMEOUT_MILLIS);
            if (line == null) continue; // nothing yet — poll the session again before waiting more
            promptPending = false;

            if (!handle(line, state, session, out)) return;
        }
    }

    /** Returns false if this line was "quit" — the loop must stop immediately. */
    private static boolean handle(String line, ClientMatchState state, ClientMatchSession session, PrintStream out) {
        ParseResult result = CommandParser.parse(line);
        if (result instanceof ParseResult.Error err) {
            out.println("error: " + err.message());
            return true;
        }
        CliCommand command = ((ParseResult.Ok) result).command();

        if (command instanceof CliCommand.Quit) {
            out.println("quit");
            return false;
        }
        if (command instanceof CliCommand.Board) {
            out.println(state != null ? StateRenderer.renderBoard(state) : "(no state yet)");
        } else if (command instanceof CliCommand.Legal) {
            out.println(StateRenderer.renderLegalActions(state != null ? state.getLegalActions() : null));
        } else if (command instanceof CliCommand.Show s) {
            out.println(state != null ? StateRenderer.renderPiece(state, s.pieceId()) : "(no state yet)");
        } else {
            long seq = CommandDispatcher.dispatch(command, session);
            if (seq == ClientCommands.NOT_SENT) {
                out.println("(not sent — this screen has no acting seat right now)");
            }
        }
        return true;
    }
}
