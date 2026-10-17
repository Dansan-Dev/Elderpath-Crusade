package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.bot.BotManager;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.utils.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The server-side PlayerInputPort: routes each pick to the responder for its owner's slot —
 * the bot responder for a bot slot, a registered remote responder for a networked slot, else
 * the local UI responder — and validates every answer before game logic sees it (pending pick
 * id, right owner, choice among the candidates).
 *
 * Each owner has at most one active pick; further requests for the same owner queue behind it
 * and are dispatched in order. While any pick is pending for an owner, GameServer rejects that
 * owner's other actions (ActionResult.Reason.PICK_PENDING).
 */
public class PickRouter implements PlayerInputPort {

    private static final class Pending {
        final PickRequest request;
        final Consumer<PickResponse> onAnswer;
        final PickResponder responder;
        boolean dispatched;

        Pending(PickRequest request, Consumer<PickResponse> onAnswer, PickResponder responder) {
            this.request = request;
            this.onAnswer = onAnswer;
            this.responder = responder;
        }

        PickRequest request() { return request; }
        Consumer<PickResponse> onAnswer() { return onAnswer; }
        PickResponder responder() { return responder; }
    }

    private final Map<PieceAlignment, Deque<Pending>> pending = new EnumMap<>(PieceAlignment.class);
    private final Map<PieceAlignment, PickResponder> remoteResponders = new EnumMap<>(PieceAlignment.class);
    private final PickResponder botResponder = new BotPickResponder();
    private PickResponder localResponder;
    private long nextPickId = 1;

    /** The responder for human slots played on this machine (null in a headless context: such picks are cancelled). */
    public void setLocalResponder(PickResponder localResponder) {
        this.localResponder = localResponder;
    }

    /** Routes owner's picks to a remote client (null removes it). */
    public void setRemoteResponder(PieceAlignment owner, PickResponder responder) {
        if (responder == null) remoteResponders.remove(owner);
        else remoteResponders.put(owner, responder);
    }

    @Override
    public PickRequest newRequest(PieceAlignment owner, PickKind kind, List<Coord> candidates, String source, boolean cancellable) {
        return new PickRequest(nextPickId++, owner, kind, candidates, source, cancellable);
    }

    @Override
    public void requestPick(PickRequest request, Consumer<PickResponse> onAnswer) {
        if (request.candidates().isEmpty()) {
            onAnswer.accept(PickResponse.cancelled(request.pickId()));
            return;
        }
        PickResponder responder = responderFor(request.owner());
        if (responder == null) {
            Logger.log("PickRouter", "No responder for " + request.owner() + " — cancelling pick " + request.source());
            onAnswer.accept(PickResponse.cancelled(request.pickId()));
            return;
        }
        Deque<Pending> queue = pending.computeIfAbsent(request.owner(), k -> new ArrayDeque<>());
        queue.addLast(new Pending(request, onAnswer, responder));
        if (queue.size() == 1) dispatchNext(request.owner());
    }

    /**
     * Delivers from's answer to its active pick. Rejected (and the pick stays pending) if from
     * has no pick with that id, if the choice isn't a candidate, or if it cancels a
     * non-cancellable pick.
     */
    public ActionResult answer(PieceAlignment from, PickResponse response) {
        Deque<Pending> queue = from == null ? null : pending.get(from);
        Pending active = queue == null ? null : queue.peekFirst();
        if (active == null || active.request().pickId() != response.pickId()) {
            return ActionResult.rejected(ActionResult.Reason.NO_PENDING_PICK);
        }
        if (response.isCancelled() ? !active.request().cancellable()
                : !active.request().candidates().contains(response.choice())) {
            return ActionResult.rejected(ActionResult.Reason.INVALID_PICK);
        }
        queue.removeFirst();
        active.onAnswer().accept(response);
        dispatchNext(from);
        return ActionResult.ok();
    }

    @Override
    public void cancelPending(PieceAlignment owner) {
        Deque<Pending> queue = pending.remove(owner);
        if (queue == null) return;
        boolean first = true;
        for (Pending p : queue) {
            if (first && p.dispatched) p.responder().onPickCancelled(p.request());
            first = false;
            p.onAnswer().accept(PickResponse.cancelled(p.request().pickId()));
        }
    }

    /** Cancels every pending pick (turn end). */
    public void cancelAll() {
        for (PieceAlignment owner : new ArrayList<>(pending.keySet())) cancelPending(owner);
    }

    /** Drops every pending pick without answering it — the match they belonged to is gone. */
    public void reset() {
        for (Deque<Pending> queue : pending.values()) {
            Pending active = queue.peekFirst();
            if (active != null && active.dispatched) active.responder().onPickCancelled(active.request());
        }
        pending.clear();
    }

    @Override
    public boolean hasPending(PieceAlignment owner) {
        Deque<Pending> queue = owner == null ? null : pending.get(owner);
        return queue != null && !queue.isEmpty();
    }

    /** owner's active (first queued) pick, or null. */
    public PickRequest activeRequest(PieceAlignment owner) {
        Deque<Pending> queue = owner == null ? null : pending.get(owner);
        Pending active = queue == null ? null : queue.peekFirst();
        return active == null ? null : active.request();
    }

    private void dispatchNext(PieceAlignment owner) {
        Deque<Pending> queue = pending.get(owner);
        if (queue == null) return;
        if (queue.isEmpty()) {
            pending.remove(owner);
            return;
        }
        // An answer callback may itself queue a follow-up pick (and dispatch it) before
        // control returns here — dispatch each pick exactly once.
        Pending next = queue.peekFirst();
        if (next.dispatched) return;
        next.dispatched = true;
        next.responder().onPickRequested(next.request(), this);
    }

    private PickResponder responderFor(PieceAlignment owner) {
        if (BotManager.isBotControlled(owner)) return botResponder;
        PickResponder remote = remoteResponders.get(owner);
        return remote != null ? remote : localResponder;
    }
}
