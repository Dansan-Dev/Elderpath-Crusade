package io.github.forest_of_dreams.cards;

import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.GamePieceStats;
import io.github.forest_of_dreams.game_objects.cards.SummonCard;
import io.github.forest_of_dreams.characters.pieces.WolfCub;
import java.util.List;

public class WolfCubCard extends SummonCard {

    public WolfCubCard(
        Board board, PieceAlignment alignment,
        int x, int y, int width, int height, int z
    ) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() {
        // WolfCub baseline: cost 0, hp 1, dmg 0, speed 1, actions 1
        return GamePieceStats.getMonsterStats(
            0,
            1,
            0,
            1,
            1
        );
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
