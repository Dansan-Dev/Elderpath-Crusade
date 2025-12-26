package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.Crow;

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
    protected GamePieceStats buildStats() {
        return GamePieceStats.getMonsterStats(3, 1, 1, 1, 3);
    }

    @Override
    protected String getCardName() { return "Crow"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new Crow(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(); }
}
