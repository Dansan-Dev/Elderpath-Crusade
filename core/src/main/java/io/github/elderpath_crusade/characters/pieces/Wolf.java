package io.github.elderpath_crusade.characters.pieces;

import io.github.elderpath_crusade.abilities.impl.PackHunterAbility;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.WolfSprite;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.__super__.CheckerSprite;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;

import java.util.UUID;
import java.util.List;

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

    public Wolf(GamePieceStats stats, int x, int y, int width, int height, PieceAlignment alignment) {
        super(
            stats,
            GamePieceType.MONSTER,
            alignment,
            UUID.randomUUID(),
            new WolfSprite(
                x, y,
                width, height,
                switch (alignment) {
                    case P1 -> CheckerSprite.AlignmentColor.BLUE;
                    case P2 -> CheckerSprite.AlignmentColor.RED;
                    default -> throw new IllegalArgumentException("Alignment must be allied or hostile");
                }
            )
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
            new WolfSprite(
                x, y,
                width, height,
                switch (alignment) {
                    case P1 -> CheckerSprite.AlignmentColor.BLUE;
                    case P2 -> CheckerSprite.AlignmentColor.RED;
                    default -> throw new IllegalArgumentException("Alignment must be allied or hostile");
                }
            )
        );
        // Example passive ability: +1 damage when adjacent to an allied Wolf
        this.addAbility(new PackHunterAbility());
    }
}
