package io.github.forest_of_dreams.characters.pieces;

import io.github.forest_of_dreams.abilities.impl.OnSummonShockAbility;
import io.github.forest_of_dreams.characters.sprites.checker_sprites.ShocklingSprite;
import io.github.forest_of_dreams.characters.sprites.checker_sprites.__super__.CheckerSprite;
import io.github.forest_of_dreams.characters.sprites.checker_sprites.WarpMageSprite; // reuse a simple sprite for demo
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.settings.GamePieceType;
import io.github.forest_of_dreams.game_objects.board.GamePieceStats;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;

import java.util.UUID;

/**
 * A small electric creature that shocks adjacent pieces upon being summoned.
 */
public class Shockling extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        // cost, hp, dmg, speed, actions
        return GamePieceStats.getMonsterStats(1, 1, 0, 1, 1);
    }

    public Shockling(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            // Using WarpMageSprite for placeholder visuals to avoid asset additions
            new ShocklingSprite(
                x, y,
                width, height,
                switch (alignment) {
                    case P1 -> CheckerSprite.AlignmentColor.BLUE;
                    case P2 -> CheckerSprite.AlignmentColor.RED;
                    default -> throw new IllegalArgumentException("Alignment must be allied or hostile");
                }
            )
        );
        // Attach triggered ability: On Summon shock adjacent pieces
        this.addAbility(new OnSummonShockAbility());
    }

    public Shockling(int x, int y, int width, int height, PieceAlignment alignment) {
        this(getBaselineStats(), x, y, width, height, alignment);
    }
}
