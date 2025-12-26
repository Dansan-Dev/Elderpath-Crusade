package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl._base.BaseAttackAbility;
import io.github.elderpath_crusade.abilities.impl.passive.CrossbowmanRangeAbility;
import io.github.elderpath_crusade.abilities.impl.trigger.ExcessDamageCarryOverAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.OncePerTurnAttackAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Crossbowman unit.
 * Ranged 2: Can attack enemies up to 2 squares away.
 * Can only attack once per turn.
 * Excess damage from attacks carries over to the closest enemy unit behind the target in range.
 */
public class Crossbowman extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(3, 1, 2, 1, 2);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Crossbowman",
            alignment
        );
    }

    public Crossbowman(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Remove default BaseAttackAbility and replace with OncePerTurnAttackAbility
        var abilitiesToRemove = getAbilities().stream()
            .filter(ability -> ability instanceof BaseAttackAbility)
            .toList();
        abilitiesToRemove.forEach(this::removeAbility);

        // Passive: +2 range
        this.addAbility(new CrossbowmanRangeAbility());
        // Basic: Once-per-turn attack
        this.addAbility(new OncePerTurnAttackAbility(this));
        // Triggered: Excess damage carry over
        this.addAbility(new ExcessDamageCarryOverAbility());
    }

    public Crossbowman(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Remove default BaseAttackAbility and replace with OncePerTurnAttackAbility
        var abilitiesToRemove = getAbilities().stream()
            .filter(ability -> ability instanceof BaseAttackAbility)
            .toList();
        abilitiesToRemove.forEach(this::removeAbility);

        // Passive: +2 range
        this.addAbility(new CrossbowmanRangeAbility());
        // Basic: Once-per-turn attack
        this.addAbility(new OncePerTurnAttackAbility(this));
        // Triggered: Excess damage carry over
        this.addAbility(new ExcessDamageCarryOverAbility());
    }
}
