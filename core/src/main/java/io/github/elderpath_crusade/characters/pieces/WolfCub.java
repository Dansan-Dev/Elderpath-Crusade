package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A smaller wolf unit with baseline low stats. Receives +1 attack when adjacent to an allied Wolf
 * (implemented via abilities when applicable).
 */
public class WolfCub extends MonsterGamePiece {

    private static GamePieceStats getBaselineStats() {
        // cost, hp, dmg, speed, actions
        return GamePieceStats.getMonsterStats(0, 1, 0, 1, 1);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Wolf Cub",
            alignment
        );
    }

    public WolfCub(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Abilities: Attach piece-specific abilities here in future (e.g., Pack Hunter passive)
        // Example (later): this.addAbility(new PackHunter());
    }

    public WolfCub(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Abilities for baseline ctor would also be attached in the main ctor above.
    }
}
