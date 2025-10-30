package io.github.elderpath_crusade.characters.sprites.checker_sprites;

import io.github.elderpath_crusade.characters.sprites.checker_sprites.__super__.CheckerSprite;
import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Generic checker-based sprite that renders a colored checker with a custom name label.
 * Useful as a placeholder sprite for new units without bespoke art.
 */
public class NamedCheckerSprite extends CheckerSprite {
    public NamedCheckerSprite(int x, int y, int width, int height, String name, PieceAlignment alignment) {
        super(x, y, width, height, name, CheckerSprite.getAlignmentColor(alignment));
    }
}
