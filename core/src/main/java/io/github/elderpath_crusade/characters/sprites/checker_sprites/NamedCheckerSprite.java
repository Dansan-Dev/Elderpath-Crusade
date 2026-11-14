package io.github.elderpath_crusade.characters.sprites.checker_sprites;

import io.github.elderpath_crusade.characters.sprites.checker_sprites.__super__.CheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.path_loaders.ImagePathPieceSprites;

/**
 * Generic checker-based sprite: a side-colored checker with the piece's figure art on top
 * ({@link ImagePathPieceSprites}), or its name label for a piece with no art yet.
 */
public class NamedCheckerSprite extends CheckerSprite {
    public NamedCheckerSprite(int x, int y, int width, int height, String name, PieceAlignment alignment) {
        super(x, y, width, height, name, CheckerSprite.getAlignmentColor(alignment));
    }

    public NamedCheckerSprite(int x, int y, int width, int height, String name, PieceAlignment alignment,
                              ImagePathPieceSprites figure) {
        super(x, y, width, height, name, CheckerSprite.getAlignmentColor(alignment), figure);
    }
}
