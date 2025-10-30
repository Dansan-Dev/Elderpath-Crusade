package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl.PackHunterAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;

import java.util.UUID;
import java.util.function.Supplier;

public class Wolf extends MonsterGamePiece {

    private static GamePieceStats getBaselineStats() {
        return GamePieceStats.getMonsterStats(
            1,
            1,
            1,
            1,
            1
        );
    }

    private static Supplier<NamedCheckerSprite> getNamedCheckerSprite(int x, int y, int width, int height, PieceAlignment alignment) {
        return () -> new NamedCheckerSprite(
            x, y,
            width, height,
            "Wolf",
            alignment
        );
    }

    public Wolf(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Example passive ability: +1 damage when adjacent to an allied Wolf
        this.addAbility(new PackHunterAbility());
    }

    public Wolf(int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            getBaselineStats(),
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            getNamedCheckerSprite(x, y, width, height, alignment).get()
        );
        // Example passive ability: +1 damage when adjacent to an allied Wolf
        this.addAbility(new PackHunterAbility());
    }
}
