package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;
import io.github.elderpath_crusade.game_objects.board.GamePiece;

/**
 * Transitional component: holds a back-reference to the OOP GamePiece
 * so legacy code (bot, board nav, card preview) can still resolve Entity → GamePiece.
 */
public class PieceRefComponent implements Component {
    public GamePiece piece;

    public PieceRefComponent set(GamePiece piece) {
        this.piece = piece;
        return this;
    }
}
