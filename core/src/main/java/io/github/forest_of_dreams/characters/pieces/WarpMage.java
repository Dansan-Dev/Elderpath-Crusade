package io.github.forest_of_dreams.characters.pieces;

import io.github.forest_of_dreams.characters.sprites.checker_sprites.WarpMageSprite;
import io.github.forest_of_dreams.characters.sprites.checker_sprites.__super__.CheckerSprite;
import io.github.forest_of_dreams.game_objects.board.GamePieceStats;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.settings.GamePieceType;

import java.util.UUID;

public class WarpMage extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(
            0,
            1,
            0,
            1,
            2
        );
    }

    public WarpMage(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            new WarpMageSprite(
                x, y,
                width, height,
                switch (alignment) {
                    case P1 -> CheckerSprite.AlignmentColor.BLUE;
                    case P2 -> CheckerSprite.AlignmentColor.RED;
                    default -> throw new IllegalArgumentException("Alignment must be allied or hostile");
                }
            )
        );
        // Attach actionable ability: Displace
        this.addAbility(new io.github.forest_of_dreams.abilities.impl.DisplaceAbility(this));
    }

    public WarpMage(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            new WarpMageSprite(
                x, y,
                width, height,
                switch (alignment) {
                    case P1 -> CheckerSprite.AlignmentColor.BLUE;
                    case P2 -> CheckerSprite.AlignmentColor.RED;
                    default -> throw new IllegalArgumentException("Alignment must be allied or hostile");
                }
            )
        );
        // Attach actionable ability: Displace
        this.addAbility(new io.github.forest_of_dreams.abilities.impl.DisplaceAbility(this));
    }
}
