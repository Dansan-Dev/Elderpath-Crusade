package io.github.forest_of_dreams.characters.sprites.monster_sprites;

import io.github.forest_of_dreams.game_objects.CharacterSprite;

public class GoblinSprite extends CharacterSprite {
    public GoblinSprite(int x, int y, int width, int height) {
        super(x, y, width, height);
        initializeAnimations();
    }

    private void initializeAnimations() {
        makeAnimationOfSpriteSheetRow(
            "walk",
            "images/gobu_walk.png",
            6,
            0, 0,
            32, 32,
            6
        );

        makeAnimationOfSpriteSheetRow(
            "hurt",
            "images/gobu_hurt.png",
            6,
            0, 0,
            32, 32,
            6
        );
    }
}
