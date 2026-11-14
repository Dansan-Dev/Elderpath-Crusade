package io.github.elderpath_crusade.data_objects.settings;

import io.github.elderpath_crusade.enums.settings.Language;

/**
 * A snapshot of every user-adjustable setting (FEAT-015/A2) — the whole point of this record is
 * letting a setting survive past a single process. Before this, the Deck Collection
 * (`~/.elderpathcrusade/collection.json`) was the only thing in the project that did; every
 * setting, including the one existing fullscreen toggle, reset to defaults on every launch.
 * Plain data, no Gdx — {@code SettingsManager} is the only thing that reads/writes it against
 * the live {@link SoundSetting}/{@link LanguageSetting}/{@code ScreenSize}.
 */
public record PersistedSettings(int masterVolume, int musicVolume, int sfxVolume, Language language, boolean fullscreen) {
    public static final PersistedSettings DEFAULTS = new PersistedSettings(5, 5, 5, Language.ENGLISH, false);
}
