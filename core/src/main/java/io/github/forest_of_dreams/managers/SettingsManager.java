package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.data_objects.settings.LanguageSetting;
import io.github.forest_of_dreams.data_objects.settings.ScreenSize;
import io.github.forest_of_dreams.data_objects.settings.SoundSetting;
import lombok.Getter;

public class SettingsManager {
    @Getter private static final int FPS = 60;

    public static final LanguageSetting language = new LanguageSetting();
    public static final SoundSetting sound = new SoundSetting();
    public static final ScreenSize screenSize = new ScreenSize();

    public static void initialize() {
        language.initialize();
        InputManager.initialize();
        ShaderManager.initialize();
    }

    public static int getScreenWidth() {
        return screenSize.getCurrentSize()[0];
    }

    public static int getScreenHeight() {
        return screenSize.getCurrentSize()[1];
    }
}
