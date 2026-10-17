package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.List;
import java.util.function.Consumer;

/**
 * The single way game logic asks a player for input mid-resolution. Logic never talks to the
 * UI or a bot directly: it hands a PickRequest to this port and continues in onAnswer, which
 * receives the validated choice — or a cancelled response, after which the logic must do
 * nothing (no effect, no cost). Who answers (local click, bot, remote client) is the port's
 * concern — see PickRouter.
 */
public interface PlayerInputPort {

    /** Creates a request with a fresh pick id. */
    PickRequest newRequest(PieceAlignment owner, PickKind kind, List<Coord> candidates, String source, boolean cancellable);

    /** Asks request.owner() to pick; onAnswer runs once, with the choice or a cancellation. */
    void requestPick(PickRequest request, Consumer<PickResponse> onAnswer);

    /** Cancels every pick pending for owner (each onAnswer receives a cancellation). */
    void cancelPending(PieceAlignment owner);

    /** True while owner has a pick waiting for an answer. */
    boolean hasPending(PieceAlignment owner);
}
