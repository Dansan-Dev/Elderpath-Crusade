package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;

import java.util.List;

/**
 * Crow card. No abilities yet.
 * JSON rules text (for future implementation): ""
 */
public class CrowCard extends SummonCard {
    public CrowCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getRegistryKey() { return "Crow"; }

    @Override
    protected String getCardName() { return "Crow"; }


    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(); }
}
