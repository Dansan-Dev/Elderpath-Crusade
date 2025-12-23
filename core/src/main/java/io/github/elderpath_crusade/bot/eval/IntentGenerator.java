package io.github.elderpath_crusade.bot.eval;

import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.bot.eval.BotActionContext.Intent;
import io.github.elderpath_crusade.bot.eval.BotActionContext.TacticalState;

import java.util.List;

/**
 * Interface for generating a list of candidate intents based on the current
 * tactical state.
 */
@FunctionalInterface
public interface IntentGenerator {
    /**
     * Build candidate intents and add them to the output list.
     *
     * @param board    The current board state
     * @param tactical Precomputed tactical state (allies, enemies, threats)
     * @param output   The list to append generated intents to
     */
    void build(Board board, TacticalState tactical, List<Intent> output);
}
