package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.passive.RiflemanRangeAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;

import java.util.List;

/**
 * Rifleman card. Ranged 2 via passive.
 */
public class RiflemanCard extends SummonCard {
    public RiflemanCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getRegistryKey() { return "Rifleman"; }

    @Override
    protected String getCardName() { return "Rifleman"; }


    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(RiflemanRangeAbility.getAbilityDescription()); }
}
