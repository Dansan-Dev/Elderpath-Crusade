package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl._multi.aura.KingEnemyAuraAbility;
import io.github.elderpath_crusade.abilities.impl._multi.aura.KingFriendlyAuraAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * King unit.
 * Passive 1: All enemy pieces within 1 range gain +1 action (max actions increased by 1).
 * Passive 2: All other friendly pieces gain +1 max health.
 */
public class King extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(3, 2, 0, 1, 1);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "King",
            alignment
        );
    }

    public King(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Add both aura abilities directly
        this.addAbility(new KingEnemyAuraAbility());
        this.addAbility(new KingFriendlyAuraAbility());
    }

    public King(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Add both aura abilities directly
        this.addAbility(new KingEnemyAuraAbility());
        this.addAbility(new KingFriendlyAuraAbility());
    }
}
