package io.github.elderpath_crusade.config;

import io.github.elderpath_crusade.data_objects.settings.LanguageSetting;
import io.github.elderpath_crusade.data_objects.settings.ScreenSize;
import io.github.elderpath_crusade.data_objects.settings.SoundSetting;
import lombok.Getter;

public class SettingsManager {
    @Getter private final int FPS = 60;

    public final LanguageSetting language = new LanguageSetting();
    public final SoundSetting sound = new SoundSetting();
    public final ScreenSize screenSize = new ScreenSize();
    public final DebugSettings debug = new DebugSettings();

    public SettingsManager() {}

    public void initialize() {
        screenSize.initialize();
        language.initialize();
    }

    public static final class DebugSettings {
        public boolean eventsLoggerInDemo = true;
        public boolean enableP2Bot = true;
    }
}
