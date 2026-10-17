package io.github.elderpath_crusade.session;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.BotManager;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Which side(s) this screen's human player may act for — the click layer's half of action
 * authority (the server's half is GameServer validating the actor it's given). Every local
 * click derives its actor from actingSide(), so a screen can never request an action for a
 * side it doesn't control, e.g. a bot-controlled one.
 *
 * - online: only the local side
 * - hotseat: whichever human side's turn it is
 * - vs bot: every side without a bot slot (the human's)
 */
public class LocalControl {

    /** Whether this screen's player may act for side (select its pieces, play its cards). */
    public boolean controls(PieceAlignment side) {
        if (side == null) return false;
        if (mode() == GameMode.ONLINE_MATCH) {
            return side == GameContext.get().getOnlineMatch().getLocalAlignment();
        }
        return !BotManager.isBotControlled(side);
    }

    /** The side this screen acts as right now — the current player if it's a side this screen controls, else null. */
    public PieceAlignment actingSide() {
        PieceAlignment current = GameContext.get().getTurnManager().getCurrentPlayer();
        return controls(current) ? current : null;
    }

    private GameMode mode() {
        return GameContext.get().getGameModeManager().getCurrent();
    }
}
