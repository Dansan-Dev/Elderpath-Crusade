package io.github.elderpath_crusade.enums.settings;

import io.github.elderpath_crusade.game_objects.pause.pages.PauseMenuPage;
import io.github.elderpath_crusade.game_objects.pause.pages.PauseSettingsPage;
import io.github.elderpath_crusade.supers.HigherOrderTexture;
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
