package io.github.elderpath_crusade.net.protocol;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.match.ActionResult;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.PickRequest;

import java.util.List;
import java.util.Map;

/**
 * Match server → client. Everything a client receives has already been filtered for it
 * (see server.HiddenInfoFilter): it never sees the opponent's card names before they're
 * played, any deck contents, or the opponent's picks.
 */
public sealed interface ServerMessage permits
        ServerMessage.Welcome,
        ServerMessage.Snapshot,
        ServerMessage.Event,
        ServerMessage.StateSync,
        ServerMessage.LegalActionsUpdate,
        ServerMessage.PickRequested,
        ServerMessage.PickAnswered,
        ServerMessage.PickCancelled,
        ServerMessage.Rejected,
        ServerMessage.Ack,
        ServerMessage.MatchEnded,
        ServerMessage.Error {

    /** The seat this client got and the match's fixed setup. slots: side → "HUMAN" or "BOT_<kind>". */
    record Welcome(int protocolVersion, PieceAlignment seat, int rows, int cols, List<Coord> terrain,
                   Map<PieceAlignment, String> slots) implements ServerMessage {}

    /** Everything this client may know right now — sent on joining (also mid-match). */
    record Snapshot(MatchView view) implements ServerMessage {}

    /** Something that happened, in order. For display/animation; StateSync is the truth for piece stats. */
    record Event(GameEvent event) implements ServerMessage {}

    /**
     * The authoritative public state of every piece, sent at the end of any server tick in
     * which it changed. Covers what no event carries (auras, statuses, action resets).
     */
    record StateSync(List<MatchView.PieceView> pieces) implements ServerMessage {}

    /**
     * Everything this client's seat may do now (see match.LegalActions) — sent at the end of
     * any tick in which it changed (after StateSync, before the tick's Acks) and on joining.
     * Only the seat whose turn it is gets non-empty lists.
     */
    record LegalActionsUpdate(LegalActions actions) implements ServerMessage {}

    /** The server needs this client to choose (answer with NetworkCommand.AnswerPick). */
    record PickRequested(PickRequest request) implements ServerMessage {}

    /** The server accepted this client's answer to pickId. */
    record PickAnswered(long pickId) implements ServerMessage {}

    /** The server dropped pickId (turn ended, match over) — clear the prompt. */
    record PickCancelled(long pickId) implements ServerMessage {}

    /** The client's command seq was rejected. */
    record Rejected(long seq, ActionResult.Reason reason) implements ServerMessage {}

    /**
     * The server has handled the client's command seq (accepted or rejected) and has sent
     * everything that command caused in that tick — sent at the end of the tick, after the
     * StateSync, so once a client has its Ack, nothing from that command is still in flight.
     */
    record Ack(long seq) implements ServerMessage {}

    record MatchEnded(PieceAlignment winner) implements ServerMessage {}

    record Error(Code code, String message) implements ServerMessage {
        public enum Code {
            VERSION_MISMATCH,
            MATCH_FULL,
            SEAT_TAKEN,
            NOT_SEATED,
            BAD_MESSAGE
        }
    }
}
