package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl.passive.SniperRangeAbility;
import io.github.elderpath_crusade.abilities.impl.trigger.StunSelfOnAttackAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sniper unit.
 * Ranged 3: Can attack enemies up to 3 squares away.
 * ON ATTACK: Get stunned for 2 turns (cannot act during stun).
 */
public class Sniper extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(2, 1, 1, 1, 1);
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Sniper",
            alignment
        );
    }

    public Sniper(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Passive: +3 range
        this.addAbility(new SniperRangeAbility());
        // Triggered: Self-stun on attack
        this.addAbility(new StunSelfOnAttackAbility());
    }

    public Sniper(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Passive: +3 range
        this.addAbility(new SniperRangeAbility());
        // Triggered: Self-stun on attack
        this.addAbility(new StunSelfOnAttackAbility());
    }
}
