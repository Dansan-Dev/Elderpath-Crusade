package io.github.elderpath_crusade.config;

import io.github.elderpath_crusade.data_objects.settings.PersistedSettings;

/** Where {@link SettingsManager} reads/writes the persisted settings blob (FEAT-015/A2). */
public interface SettingsStore {
    /** {@link PersistedSettings#DEFAULTS} if nothing has ever been saved. */
    PersistedSettings load();

    void save(PersistedSettings settings);
}
