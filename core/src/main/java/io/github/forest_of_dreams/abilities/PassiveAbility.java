package io.github.forest_of_dreams.abilities;

import io.github.forest_of_dreams.abilities.stats.StatsModifier;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;

/**
 * Passive abilities provide ongoing modifiers or rules that influence gameplay while attached.
 * Each passive owns a single StatsModifier instance (typically affecting one stat) and adds/removes
 * it from the owner's accumulator based on its condition.
 */
public interface PassiveAbility extends Ability {
    @Override
    default AbilityType getType() { return AbilityType.PASSIVE; }

    /** A reusable modifier instance owned by this ability. Its 'source' should be set to 'this'. */
    StatsModifier getModifier();

    /** Whether the condition for this passive is currently met. */
    boolean isConditionMet(MonsterGamePiece owner, Board board);
}
