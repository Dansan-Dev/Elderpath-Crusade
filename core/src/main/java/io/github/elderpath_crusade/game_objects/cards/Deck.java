package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.gdx.graphics.g2d.Sprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.game_objects.sprites.SpriteObject;
import io.github.elderpath_crusade.path_loaders.ImagePathSpritesAndAnimations;
import io.github.elderpath_crusade.utils.SpriteCreator;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** The draw pile's view: a card back. The deck's cards live in DeckState. */
public class Deck extends SpriteObject {
    // Owner of this deck (P1 or P2)
    @Getter @Setter
    private PieceAlignment owner;

    public Deck(int x, int y, int width, int height, int z, SpriteBoxPos spriteBoxPos) {
        super(x, y, width, height, z, spriteBoxPos);

        Sprite sprite = SpriteCreator.makeSprite(
            ImagePathSpritesAndAnimations.CARD_BACK.getPath(),
            0, 0,
            1024, 1536,
            125, 200
        );
        addAnimation(
            "general",
            List.of(sprite),
            0
        );
    }
}
