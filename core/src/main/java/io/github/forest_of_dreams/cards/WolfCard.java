package io.github.forest_of_dreams.cards;

import com.badlogic.gdx.graphics.Color;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.cards.SummonCard;
import io.github.forest_of_dreams.characters.pieces.Wolf;

/**
 * WolfCard now extends SummonCard, providing cost, name, and piece instantiation.
 * Title and border animation are handled by Card.
 */
public class WolfCard extends SummonCard {

    public WolfCard(
        Board board, PieceAlignment alignment,
        int x, int y, int width, int height, int z
    ) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected int getCost() { return 1; }

    @Override
    protected String getCardName() { return "Wolf"; }

    @Override
    protected GamePiece instantiatePiece() {
        return new Wolf(
            0, 0,
            board.getPLOT_WIDTH(),
            board.getPLOT_HEIGHT(),
            alignment
        );
    }
}
