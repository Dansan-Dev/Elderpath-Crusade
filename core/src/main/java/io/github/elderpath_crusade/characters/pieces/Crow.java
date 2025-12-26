package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Crow unit. No abilities.
 */
public class Crow extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        // cost, hp, dmg, speed(=1), actions
        return GamePieceStats.getMonsterStats(3, 1, 1, 1, 3);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Crow",
            alignment
        );
    }

    public Crow(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // No abilities attached yet.
    }

    public Crow(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // No abilities attached yet.
    }
}
