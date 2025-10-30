package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.Sniper;

import java.util.List;

/**
 * Sniper card. TODO rules: Ranged 3; On attack, gets stunned for 2 turns
 */
public class SniperCard extends SummonCard {
    public SniperCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() { return GamePieceStats.getMonsterStats(2, 1, 1, 1, 1); }

    @Override
    protected String getCardName() { return "Sniper"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new Sniper(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(); }
}
