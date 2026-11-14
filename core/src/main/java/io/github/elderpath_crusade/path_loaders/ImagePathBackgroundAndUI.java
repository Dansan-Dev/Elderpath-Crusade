package io.github.elderpath_crusade.path_loaders;

import lombok.Getter;

@Getter
public enum ImagePathBackgroundAndUI {
    HOME_NAVBAR("home_navbar.png"),
    HOME_BACKGROUND("home_screen_background.png"),
    /** ART-005: light-grey stone plate behind every color button, drawn as a nine-patch tinted by the button's color. */
    BUTTON_PLATE("ui/button_plate.png");

    private final String path;

    ImagePathBackgroundAndUI(String path) {
        this.path = "images/" + path;
    }

}
