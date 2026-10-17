package io.github.elderpath_crusade.match;

/**
 * Whoever answers a player's picks: a local UI, a bot, or a remote client. A responder is
 * told about a request and later answers it through PickRouter.answer (synchronously or not);
 * onPickCancelled tells it the server dropped the request (turn ended, match reset), so a UI
 * can clear its prompt.
 */
public interface PickResponder {
    void onPickRequested(PickRequest request, PickRouter router);

    default void onPickCancelled(PickRequest request) {
    }
}
