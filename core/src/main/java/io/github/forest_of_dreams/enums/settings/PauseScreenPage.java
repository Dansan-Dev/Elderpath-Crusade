package io.github.forest_of_dreams.enums.settings;

import io.github.forest_of_dreams.game_objects.pause.pages.PauseMenuPage;
import io.github.forest_of_dreams.game_objects.pause.pages.PauseSettingsPage;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import lombok.Getter;

public enum PauseScreenPage {
    MENU(new PauseMenuPage()),
    SETTINGS(new PauseSettingsPage()),
    NONE(null);

    @Getter
    private HigherOrderTexture page;

    PauseScreenPage(HigherOrderTexture page) {
        this.page = page;
    }
}
