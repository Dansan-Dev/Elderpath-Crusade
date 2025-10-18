package io.github.forest_of_dreams.path_loaders;

import lombok.Getter;

@Getter
public enum ImagePathSpritesAndAnimations {
    GOBU_WALK("gobu_walk.png"),
    GOBU_HURT("gobu_hurt.png"),
    RED_CHECKER("red_checker.png"),
    BLUE_CHECKER("blue_checker.png"),
    MOUNTAIN_TERRAIN("mountain_tile.png");


    private final String path;

    ImagePathSpritesAndAnimations(String path) {
        this.path = "images/" + path;
    }
}
