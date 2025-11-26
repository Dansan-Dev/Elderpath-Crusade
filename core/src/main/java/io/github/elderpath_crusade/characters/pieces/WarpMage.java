package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl.actionable.DisplaceAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;

import java.util.UUID;
import java.util.function.Supplier;

public class WarpMage extends MonsterGamePiece {
    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(
            0,
            1,
            0,
            1,
            2
        );
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Warp Mage",
            alignment
        );
    }

    public WarpMage(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Attach actionable ability: Displace
        this.addAbility(new DisplaceAbility(this));
    }

    public WarpMage(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Attach actionable ability: Displace
        this.addAbility(new DisplaceAbility(this));
    }
}
