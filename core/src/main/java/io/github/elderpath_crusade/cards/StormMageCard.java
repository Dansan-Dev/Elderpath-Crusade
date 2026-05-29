package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl._multi.other.StormActionAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.StormMage;

import java.util.List;

/**
 * Storm Mage card. STORM ACTION (1/turn): Within 2 squares, pick a square as the center,
 * deal 2 damage in the center, and 1 damage to all squares surrounding it.
 */
public class StormMageCard extends SummonCard {
    public StormMageCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getCardName() { return "Storm Mage"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new StormMage(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(StormActionAbility.getAbilityDescription()); }
}
