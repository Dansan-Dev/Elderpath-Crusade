package io.github.elderpath_crusade.interaction;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.match.PickResponder;
import io.github.elderpath_crusade.match.PickResponse;
import io.github.elderpath_crusade.match.PickRouter;

import java.util.function.BiPredicate;

/**
 * Answers picks for the human playing on this screen: prompts via LocalUiPickPrompt and
 * submits the click (or a cancel — right click, ESC) like any client would. By default the
 * answer goes to the in-process GameServer (solo, hotseat, the host's own side); the online
 * guest constructs it with a submitter that sends the answer to the host instead.
 */
public final class LocalUiPickResponder implements PickResponder {
    /** Submits an answer; returns false if it was rejected (the pick is then still pending — ask again). */
    private final BiPredicate<PickRequest, PickResponse> submit;
    /** The pick currently prompted on screen — answers/cancels for any other id are stale. */
    private long promptedPickId = -1;

    public LocalUiPickResponder() {
        this((request, response) -> GameContext.get().getGameServer().answerPick(request.owner(), response).isOk());
    }

    public LocalUiPickResponder(BiPredicate<PickRequest, PickResponse> submit) {
        this.submit = submit;
    }

    @Override
    public void onPickRequested(PickRequest request, PickRouter router) {
        prompt(request);
    }

    /** Shows request on this screen (also how the online guest presents a relayed pick). */
    public void prompt(PickRequest request) {
        promptedPickId = request.pickId();
        boolean shown = LocalUiPickPrompt.show(request,
                choice -> answer(request, PickResponse.chose(request.pickId(), choice)),
                () -> answer(request, PickResponse.cancelled(request.pickId())));
        if (!shown) answer(request, PickResponse.cancelled(request.pickId()));
    }

    @Override
    public void onPickCancelled(PickRequest request) {
        cancelPrompt(request.pickId());
    }

    /** Clears the on-screen prompt for pickId, if it's still the one shown. */
    public void cancelPrompt(long pickId) {
        if (promptedPickId != pickId) return;
        promptedPickId = -1;
        LocalUiPickPrompt.clear();
    }

    private void answer(PickRequest request, PickResponse response) {
        if (promptedPickId != request.pickId()) return;
        promptedPickId = -1;
        if (!submit.test(request, response)) prompt(request);
    }
}
