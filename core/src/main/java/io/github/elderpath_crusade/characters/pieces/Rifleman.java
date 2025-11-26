package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl.passive.RiflemanRangeAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Rifleman unit.
 * Ranged 2 via passive ability.
 */
public class Rifleman extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(2, 1, 1, 1, 1);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Rifleman",
            alignment
        );
    }

    public Rifleman(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Passive: +2 range
        this.addAbility(new RiflemanRangeAbility());
    }

    public Rifleman(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Passive: +2 range
        this.addAbility(new RiflemanRangeAbility());
    }
}
