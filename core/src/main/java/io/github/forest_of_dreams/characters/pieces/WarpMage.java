package io.github.forest_of_dreams.characters.pieces;

import io.github.forest_of_dreams.characters.sprites.checker_sprites.WarpMageSprite;
import io.github.forest_of_dreams.characters.sprites.checker_sprites.__super__.CheckerSprite;
import io.github.forest_of_dreams.data_objects.GamePiece;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.settings.GamePieceType;
import io.github.forest_of_dreams.interfaces.Renderable;

import java.util.UUID;

public class WarpMage extends GamePiece {
    public WarpMage(int x, int y, int width, int height, PieceAlignment alignment) {
        super(GamePieceType.MONSTER, alignment, UUID.randomUUID(), new WarpMageSprite(x, y, width, height, switch (alignment) {
            case ALLIED -> CheckerSprite.AlignmentColor.BLUE;
            case HOSTILE -> CheckerSprite.AlignmentColor.RED;
            default -> throw new IllegalArgumentException("Alignment must be allied or hostile");
        }));
    }
}
