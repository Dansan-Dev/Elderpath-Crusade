package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl.BoostActionAbility;
import io.github.elderpath_crusade.abilities.impl.CannotAttackAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Wind Spirit unit.
 * Cannot Attack. BOOST ACTION: Give an adjacent friendly unit +1 action this turn.
 */
public class WindSpirit extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(1, 2, 0, 1, 1);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Wind Spirit",
            alignment
        );
    }

    public WindSpirit(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Passive: Cannot Attack
        this.addAbility(new CannotAttackAbility());
        // Actionable: BOOST ACTION
        this.addAbility(new BoostActionAbility(this));
    }

    public WindSpirit(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Passive: Cannot Attack
        this.addAbility(new CannotAttackAbility());
        // Actionable: BOOST ACTION
        this.addAbility(new BoostActionAbility(this));
    }
}
