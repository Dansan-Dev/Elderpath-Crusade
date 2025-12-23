package io.github.elderpath_crusade.bot;

import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Bot interface: a bot drives its own pacing for an entire turn.
 * BotManager will notify the bot when a turn starts for a given player.
 * The bot is responsible for scheduling its own actions and ending the turn
 * (e.g., by calling TurnManager.endTurn()) when it is done.
 */
public interface Bot {
    /**
     * @return human-readable bot name for logs.
     */
    String getName();

    /**
     * Called by BotManager when a turn starts for the specified player.
     * The bot should perform all of its actions (using whatever internal timing
     * it wants) and then end the turn when finished.
     */
    void onTurnStarted(PieceAlignment player);
}
