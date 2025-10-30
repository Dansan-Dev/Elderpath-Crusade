package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.King;

import java.util.List;

/**
 * King card. TODO rules: All enemies within 1 square has +1 action; All other friendly units has +1 health
 */
public class KingCard extends SummonCard {
    public KingCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() { return GamePieceStats.getMonsterStats(3, 2, 0, 1, 1); }

    @Override
    protected String getCardName() { return "King"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new King(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(); }
}
