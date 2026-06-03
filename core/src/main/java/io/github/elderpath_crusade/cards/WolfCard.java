package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl._multi.aura.PackHunterAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import java.util.List;

/**
 * WolfCard now extends SummonCard, providing stats, name, and piece instantiation.
 * Title and border animation are handled by Card.
 */
public class WolfCard extends SummonCard {

    public WolfCard(
        Board board, PieceAlignment alignment,
        int x, int y, int width, int height, int z
    ) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getRegistryKey() { return "Wolf"; }

    @Override
    protected String getCardName() { return "Wolf"; }


    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        return List.of(PackHunterAbility.getAbilityDescription());
    }
}
