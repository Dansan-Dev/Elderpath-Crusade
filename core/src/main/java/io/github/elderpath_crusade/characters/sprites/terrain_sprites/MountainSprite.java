package io.github.elderpath_crusade.characters.sprites.terrain_sprites;

import io.github.elderpath_crusade.path_loaders.ImagePathSpritesAndAnimations;
import io.github.elderpath_crusade.game_objects.sprites.CharacterSprite;

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
