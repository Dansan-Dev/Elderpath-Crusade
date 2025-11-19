package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl.BaseMoveAbility;
import io.github.elderpath_crusade.abilities.impl.JumpMoveAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Big Toad unit.
 * Jump Movement: Can move in cardinal directions only, jumping over terrain and units.
 */
public class BigToad extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(1, 2, 1, 2, 1);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Big Toad",
            alignment
        );
    }

    public BigToad(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Remove default BaseMoveAbility and replace with JumpMoveAbility
        // Collect abilities to remove first to avoid ConcurrentModificationException
        var abilitiesToRemove = getAbilities().stream()
            .filter(ability -> ability instanceof BaseMoveAbility)
            .toList();
        abilitiesToRemove.forEach(this::removeAbility);
        this.addAbility(new JumpMoveAbility(this));
    }

    public BigToad(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Remove default BaseMoveAbility and replace with JumpMoveAbility
        // Collect abilities to remove first to avoid ConcurrentModificationException
        var abilitiesToRemove = getAbilities().stream()
            .filter(ability -> ability instanceof BaseMoveAbility)
            .toList();
        abilitiesToRemove.forEach(this::removeAbility);
        this.addAbility(new JumpMoveAbility(this));
    }
}
