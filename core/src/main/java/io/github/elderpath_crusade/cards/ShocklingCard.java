package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.trigger.OnSummonShockAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;

import java.util.List;

public class ShocklingCard extends SummonCard {

    public ShocklingCard(
        Board board, PieceAlignment alignment,
        int x, int y, int width, int height, int z
    ) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getRegistryKey() { return "Shockling"; }

    @Override
    protected String getCardName() { return "Shockling"; }


    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        return List.of(OnSummonShockAbility.getAbilityDescription());
    }
}
