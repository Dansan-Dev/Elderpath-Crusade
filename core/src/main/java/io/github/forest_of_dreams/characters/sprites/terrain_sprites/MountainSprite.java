package io.github.forest_of_dreams.characters.sprites.terrain_sprites;

import io.github.forest_of_dreams.path_loaders.ImagePathSpritesAndAnimations;
import io.github.forest_of_dreams.game_objects.CharacterSprite;

public class MountainSprite extends CharacterSprite {
    public MountainSprite(int x, int y, int width, int height) {
        super(x, y, width, height);
        makeAnimationOfSpriteSheetRow(
            "passive",
            ImagePathSpritesAndAnimations.MOUNTAIN_TERRAIN.getPath(),
            1,
            0, 0,
            1024, 1024,
            0
        );
    }
}
