package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl._multi.aura.CommanderAuraAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Commander unit.
 * Adjacent friendly units gain +1 attack.
 */
public class Commander extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(1, 1, 0, 1, 2);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Commander",
            alignment
        );
    }

    public Commander(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Passive: +1 attack to adjacent friendly units
        this.addAbility(new CommanderAuraAbility());
    }

    public Commander(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Passive: +1 attack to adjacent friendly units
        this.addAbility(new CommanderAuraAbility());
    }
}
