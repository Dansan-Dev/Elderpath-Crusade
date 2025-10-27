package io.github.forest_of_dreams.characters.pieces;

import io.github.forest_of_dreams.characters.sprites.checker_sprites.WolfSprite;
import io.github.forest_of_dreams.characters.sprites.checker_sprites.__super__.CheckerSprite;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.settings.GamePieceType;
import io.github.forest_of_dreams.game_objects.board.GamePieceStats;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;

import java.util.UUID;

/**
 * A smaller wolf unit with baseline low stats. Receives +1 attack when adjacent to an allied Wolf
 * (implemented as a passive buff calculated by Board when resolving attacks).
 */
public class WolfCub extends MonsterGamePiece {

    private static GamePieceStats getBaselineStats() {
        // cost, hp, dmg, speed, actions
        return GamePieceStats.getMonsterStats(0, 1, 0, 1, 1);
    }

    public WolfCub(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            new WolfSprite(
                x, y,
                width, height,
                switch (alignment) {
                    case P1 -> CheckerSprite.AlignmentColor.BLUE;
                    case P2 -> CheckerSprite.AlignmentColor.RED;
                    default -> throw new IllegalArgumentException("Alignment must be allied or hostile");
                }
            )
        );
        // Abilities: Attach piece-specific abilities here in future (e.g., Pack Hunter passive)
        // Example (later): this.addAbility(new PackHunter());
    }

    public WolfCub(int x, int y, int width, int height, PieceAlignment alignment) {
        this(getBaselineStats(), x, y, width, height, alignment);
        // Abilities for baseline ctor would also be attached in the main ctor above.
    }
}
