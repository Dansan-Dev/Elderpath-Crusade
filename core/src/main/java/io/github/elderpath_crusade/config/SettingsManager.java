package io.github.elderpath_crusade.config;

import io.github.elderpath_crusade.data_objects.settings.LanguageSetting;
import io.github.elderpath_crusade.data_objects.settings.PersistedSettings;
import io.github.elderpath_crusade.data_objects.settings.ScreenSize;
import io.github.elderpath_crusade.data_objects.settings.SoundSetting;
import lombok.Getter;

import java.io.File;

/**
 * {@code sound}/{@code language}/{@code screenSize}'s own fullscreen state are restored from
 * (and saved to) a {@link SettingsStore} — FEAT-015/A2. Before this, every setting reset to
 * defaults on every launch (confirmed: the Deck Collection was the only thing in this project
 * that persisted at all). {@link #initialize()} loads and applies the saved values; every
 * setter call from the settings screen should follow with {@link #persist()} so the change
 * actually survives the next launch — see {@code SettingsRoom}.
 */
public class SettingsManager {
    @Getter private final int FPS = 60;

    public final LanguageSetting language = new LanguageSetting();
    public final SoundSetting sound = new SoundSetting();
    public final ScreenSize screenSize = new ScreenSize();
    public final DebugSettings debug = new DebugSettings();

    private final SettingsStore store;

    public SettingsManager() {
        this(new JsonSettingsStore(defaultFile()));
    }

    public SettingsManager(SettingsStore store) {
        if (store == null) throw new IllegalArgumentException("store required");
        this.store = store;
    }

    /** {@code ~/.elderpathcrusade/settings.json} — same home as the Deck Collection file, not under assets/ or any Gdx path. */
    public static File defaultFile() {
        return new File(System.getProperty("user.home"), ".elderpathcrusade/settings.json");
    }

    public void initialize() {
        screenSize.initialize();
        language.initialize();

        PersistedSettings saved = store.load();
        sound.setMasterVolume(saved.masterVolume());
        sound.setMusicVolume(saved.musicVolume());
        sound.setSFXVolume(saved.sfxVolume());
        language.setLanguage(saved.language());
        screenSize.setFullscreen(saved.fullscreen());
    }

    /** Snapshots every current setting and saves it — call after any change made from the settings screen. */
    public void persist() {
        store.save(new PersistedSettings(sound.getMasterVolume(), sound.getMusicVolume(), sound.getSFXVolume(),
                language.getLanguage(), screenSize.isFullscreen()));
    }

    public static final class DebugSettings {
        public boolean eventsLoggerInDemo = true;
        public boolean enableP2Bot = true;
        /** Sprint 64 — F3 toggles the in-game FPS/frame-time/heap/GC/music diagnostics overlay. */
        public boolean showPerfOverlay = false;
    }
}
