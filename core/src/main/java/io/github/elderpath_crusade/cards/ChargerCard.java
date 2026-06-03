package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.trigger.PushOnAttackAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;

import java.util.List;

/**
 * Charger card. ON ATTACK: Push target 1 back and move 1 forward. If blocked, target takes 1 damage.
 */
public class ChargerCard extends SummonCard {
    public ChargerCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getRegistryKey() { return "Charger"; }

    @Override
    protected String getCardName() { return "Charger"; }


    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        return List.of(PushOnAttackAbility.getAbilityDescription());
    }
}
