package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl.trigger.OnSummonShockAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A small electric creature that shocks adjacent pieces upon being summoned.
 */
public class Shockling extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        // cost, hp, dmg, speed, actions
        return GamePieceStats.getMonsterStats(1, 1, 0, 1, 1);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Shockling",
            alignment
        );
    }

    public Shockling(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Attach triggered ability: On Summon shock adjacent pieces
        this.addAbility(new OnSummonShockAbility());
    }

    public Shockling(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Attach triggered ability: On Summon shock adjacent pieces
        this.addAbility(new OnSummonShockAbility());
    }
}
