package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.passive.CrossbowmanRangeAbility;
import io.github.elderpath_crusade.abilities.impl.trigger.ExcessDamageCarryOverAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;

import java.util.List;

/**
 * Crossbowman card. Ranged 2; Can only attack once a turn; Excess damage carries over behind target
 */
public class CrossbowmanCard extends SummonCard {
    public CrossbowmanCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getRegistryKey() { return "Crossbowman"; }

    @Override
    protected String getCardName() { return "Crossbowman"; }


    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        return List.of(
            CrossbowmanRangeAbility.getAbilityDescription(),
            "Can only attack\nonce per turn",
            ExcessDamageCarryOverAbility.getAbilityDescription()
        );
    }
}
