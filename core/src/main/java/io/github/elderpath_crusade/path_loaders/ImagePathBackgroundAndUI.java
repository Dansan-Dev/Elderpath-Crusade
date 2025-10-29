package io.github.elderpath_crusade.path_loaders;

import lombok.Getter;

@Getter
public enum ImagePathBackgroundAndUI {
    HOME_NAVBAR("home_navbar.png"),
    HOME_BACKGROUND("home_screen_background.png");

    private final String path;

    ImagePathBackgroundAndUI(String path) {
        this.path = "images/" + path;
    }

}
