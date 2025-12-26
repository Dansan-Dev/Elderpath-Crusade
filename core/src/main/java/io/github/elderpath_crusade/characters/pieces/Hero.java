package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl.trigger.GrowthOnKillAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Hero unit.
 * ON KILL: gain 1 attack and heal 1.
 */
public class Hero extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(2, 2, 1, 1, 1);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Hero",
            alignment
        );
    }

    public Hero(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Triggered: ON KILL: gain 1 attack and heal 1
        this.addAbility(new GrowthOnKillAbility());
    }

    public Hero(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Triggered: ON KILL: gain 1 attack and heal 1
        this.addAbility(new GrowthOnKillAbility());
    }
}
