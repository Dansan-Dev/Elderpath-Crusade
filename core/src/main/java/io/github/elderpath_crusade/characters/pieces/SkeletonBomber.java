package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl.BaseAttackAbility;
import io.github.elderpath_crusade.abilities.impl.BombActionAbility;
import io.github.elderpath_crusade.abilities.impl.CannotAttackAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Skeleton Bomber unit.
 * Cannot Attack: Cannot perform normal attacks.
 * BOMB ACTION (1/turn): Deal damage equal to its attack to all units within 1 square.
 */
public class SkeletonBomber extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(2, 3, 2, 1, 1);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Skeleton Bomber",
            alignment
        );
    }

    public SkeletonBomber(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Remove default BaseAttackAbility (CannotAttackAbility prevents attacks anyway, but remove for consistency)
        var abilitiesToRemove = getAbilities().stream()
            .filter(ability -> ability instanceof BaseAttackAbility)
            .toList();
        abilitiesToRemove.forEach(this::removeAbility);

        // Passive: Cannot Attack
        this.addAbility(new CannotAttackAbility());
        // Actionable: BOMB ACTION
        this.addAbility(new BombActionAbility(this));
    }

    public SkeletonBomber(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Remove default BaseAttackAbility (CannotAttackAbility prevents attacks anyway, but remove for consistency)
        var abilitiesToRemove = getAbilities().stream()
            .filter(ability -> ability instanceof BaseAttackAbility)
            .toList();
        abilitiesToRemove.forEach(this::removeAbility);

        // Passive: Cannot Attack
        this.addAbility(new CannotAttackAbility());
        // Actionable: BOMB ACTION
        this.addAbility(new BombActionAbility(this));
    }
}
