package io.github.forest_of_dreams.characters.pieces.monster;

import io.github.forest_of_dreams.characters.sprites.monster_sprites.GoblinSprite;
import io.github.forest_of_dreams.data_objects.GamePiece;
import io.github.forest_of_dreams.data_objects.GamePieceStats;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.settings.GamePieceType;

import java.util.UUID;

public class Goblin extends GamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(2, 1, 2, 1, 1);
    }


    public Goblin(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            new GoblinSprite(x, y, width, height)
        );
    }

    public Goblin(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            new GoblinSprite(x, y, width, height)
        );
    }
}
