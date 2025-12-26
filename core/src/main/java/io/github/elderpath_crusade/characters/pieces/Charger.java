package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl.trigger.PushOnAttackAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Charger unit.
 * ON ATTACK: Push the target 1 square backwards and move 1 square towards the target.
 * If a unit or terrain is behind the target, instead only the attacked piece takes 1 damage.
 */
public class Charger extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(2, 3, 1, 1, 1);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Charger",
            alignment
        );
    }

    public Charger(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Triggered: Push on attack
        this.addAbility(new PushOnAttackAbility());
    }

    public Charger(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Triggered: Push on attack
        this.addAbility(new PushOnAttackAbility());
    }
}
