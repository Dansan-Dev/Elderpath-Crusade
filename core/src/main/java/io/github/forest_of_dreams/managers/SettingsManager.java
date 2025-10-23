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
    public static final DebugSettings debug = new DebugSettings();

    public static void initialize() {
        screenSize.initialize();
        language.initialize();
    }

    public static final class DebugSettings {
        // When true, DemoRoom registers an all-events logger via EventBus for manual debugging.
        public boolean eventsLoggerInDemo = true;
        // Enable or disable the simple P2 bot. When false, BotManager will not act.
        public boolean enableP2Bot = true;
    }
}
