package io.github.elderpath_crusade.tiles;

import io.github.elderpath_crusade.characters.sprites.terrain_sprites.MountainSprite;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePieceStats;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;

import java.util.UUID;

public class MountainTile extends GamePiece {

    public MountainTile(int x, int y, int width, int height) {
        super(
            GamePieceStats.getTerrainStats(0, 0),
            GamePieceType.TERRAIN,
            PieceAlignment.NEUTRAL,
            UUID.randomUUID(),
            new MountainSprite(x, y, width, height)
        );
    }
}
