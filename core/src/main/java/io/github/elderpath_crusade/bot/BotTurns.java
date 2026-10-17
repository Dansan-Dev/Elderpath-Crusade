package io.github.elderpath_crusade.bot;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.ActionResult;
import io.github.elderpath_crusade.utils.Logger;

/**
 * Ends a bot's turn through GameServer so it can't be left hanging: waits out a pause instead
 * of dropping the request, and logs and retries a rejected end while the turn is still the
 * bot's. A turn that's already over (NOT_YOUR_TURN) or a finished match is fine.
 */
public final class BotTurns {
    private static final float RETRY_DELAY = 0.4f;
    private static final int MAX_ATTEMPTS = 20;

    private BotTurns() {}

    public static void endTurn(PieceAlignment side) {
        endTurn(side, 1);
    }

    private static void endTurn(PieceAlignment side, int attempt) {
        if (GameContext.get().getGameManager().isPaused()) {
            GameContext.get().getMatchClock().schedule(RETRY_DELAY, () -> endTurn(side, attempt));
            return;
        }
        ActionResult result = GameContext.get().getGameServer().endTurn(side);
        if (result.isOk()) return;
        ActionResult.Reason reason = result.reason();
        if (reason == ActionResult.Reason.NOT_YOUR_TURN || reason == ActionResult.Reason.NO_ACTIVE_MATCH) return;

        if (reason == ActionResult.Reason.PICK_PENDING) {
            // BotPickResponder answers a bot's picks synchronously, so this is a bug somewhere — don't hang on it.
            Logger.error("BotTurns", side + "'s end turn hit PICK_PENDING; cancelling the bot's stray pick");
            GameContext.get().getPickRouter().cancelPending(side);
        } else {
            Logger.error("BotTurns", side + "'s end turn was rejected: " + reason + " (attempt " + attempt + ")");
        }
        if (attempt < MAX_ATTEMPTS) {
            GameContext.get().getMatchClock().schedule(RETRY_DELAY, () -> endTurn(side, attempt + 1));
        } else {
            Logger.error("BotTurns", side + "'s turn could not be ended after " + attempt + " attempts");
        }
    }
}
