package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.trigger.GrowthOnKillAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;

import java.util.List;

/**
 * Hero card. ON KILL: gain 1 attack and heal 1.
 */
public class HeroCard extends SummonCard {
    public HeroCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getRegistryKey() { return "Hero"; }

    @Override
    protected String getCardName() { return "Hero"; }


    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(GrowthOnKillAbility.getAbilityDescription()); }
}
