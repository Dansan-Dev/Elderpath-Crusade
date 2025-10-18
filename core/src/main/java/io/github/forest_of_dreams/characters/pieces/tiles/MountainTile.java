package io.github.forest_of_dreams.characters.pieces.tiles;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.characters.sprites.terrain_sprites.MountainSprite;
import io.github.forest_of_dreams.data_objects.GamePiece;
import io.github.forest_of_dreams.data_objects.GamePieceStats;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.settings.GamePieceType;
import io.github.forest_of_dreams.interfaces.Renderable;

import java.util.List;
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
