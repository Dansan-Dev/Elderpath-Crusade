package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.BigToad;

import java.util.List;

/**
 * Big Toad card. TODO rules: JUMP ACTION: Move 2 steps in one direction, ignoring terrain in the way
 */
public class BigToadCard extends SummonCard {
    public BigToadCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() { return GamePieceStats.getMonsterStats(1, 2, 1, 1, 1); }

    @Override
    protected String getCardName() { return "Big Toad"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new BigToad(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(); }
}
