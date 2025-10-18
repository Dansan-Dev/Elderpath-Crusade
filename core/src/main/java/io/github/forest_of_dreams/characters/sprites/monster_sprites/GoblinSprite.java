package io.github.forest_of_dreams.characters.sprites.monster_sprites;

import io.github.forest_of_dreams.path_loaders.ImagePathSpritesAndAnimations;
import io.github.forest_of_dreams.game_objects.CharacterSprite;

public class GoblinSprite extends CharacterSprite {
    public GoblinSprite(int x, int y, int width, int height) {
        super(x, y, width, height);
        initializeAnimations();
    }

    private void initializeAnimations() {
        makeAnimationOfSpriteSheetRow(
            "walk",
            ImagePathSpritesAndAnimations.GOBU_WALK.getPath(),
            6,
            0, 0,
            32, 32,
            6
        );

        makeAnimationOfSpriteSheetRow(
            "hurt",
            ImagePathSpritesAndAnimations.GOBU_HURT.getPath(),
            6,
            0, 0,
            32, 32,
            6
        );
    }
}
