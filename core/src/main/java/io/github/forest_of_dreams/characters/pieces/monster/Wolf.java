package io.github.forest_of_dreams.characters.pieces.monster;

import io.github.forest_of_dreams.characters.sprites.checker_sprites.WolfSprite;
import io.github.forest_of_dreams.characters.sprites.checker_sprites.__super__.CheckerSprite;
import io.github.forest_of_dreams.game_objects.board.GamePieceStats;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.settings.GamePieceType;

import java.util.UUID;

public class Wolf extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(
            1,
            1,
            1,
            1,
            1
        );
    }

    public Wolf(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            new WolfSprite(
                x, y,
                width, height,
                switch (alignment) {
                    case ALLIED -> CheckerSprite.AlignmentColor.BLUE;
                    case HOSTILE -> CheckerSprite.AlignmentColor.RED;
                    default -> throw new IllegalArgumentException("Alignment must be allied or hostile");
                }
            )
        );
    }

    public Wolf(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            new WolfSprite(
                x, y,
                width, height,
                switch (alignment) {
                    case ALLIED -> CheckerSprite.AlignmentColor.BLUE;
                    case HOSTILE -> CheckerSprite.AlignmentColor.RED;
                    default -> throw new IllegalArgumentException("Alignment must be allied or hostile");
                }
            )
        );
    }
}
