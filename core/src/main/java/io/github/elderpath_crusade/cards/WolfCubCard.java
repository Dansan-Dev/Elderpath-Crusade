package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.WolfCub;
import java.util.List;

public class WolfCubCard extends SummonCard {

    public WolfCubCard(
        Board board, PieceAlignment alignment,
        int x, int y, int width, int height, int z
    ) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getCardName() { return "Wolf Cub"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new WolfCub(
            stats,
            0, 0,
            board.getPLOT_WIDTH(),
            board.getPLOT_HEIGHT(),
            alignment
        );
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        return List.of();
    }
}
