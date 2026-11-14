package io.github.elderpath_crusade.config;

import io.github.elderpath_crusade.data_objects.settings.PersistedSettings;
import io.github.elderpath_crusade.enums.settings.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/** Plain-{@link File}-backed, so no Gdx/GameContext needed — same pattern as collection/JsonDeckCollectionStoreTest. */
class JsonSettingsStoreTest {

    @Test
    void load_withNoFileYet_returnsDefaults(@TempDir File dir) {
        SettingsStore store = new JsonSettingsStore(new File(dir, "settings.json"));
        assertEquals(PersistedSettings.DEFAULTS, store.load());
    }

    @Test
    void save_thenLoad_roundTripsExactly(@TempDir File dir) {
        File file = new File(dir, "nested/settings.json");
        SettingsStore store = new JsonSettingsStore(file);
        PersistedSettings settings = new PersistedSettings(8, 2, 10, Language.SWEDISH, true);

        store.save(settings);

        assertTrue(file.isFile(), "save() should create the file (and its parent dir)");
        assertEquals(settings, store.load());
    }

    @Test
    void save_overwritesRatherThanAppending(@TempDir File dir) {
        File file = new File(dir, "settings.json");
        SettingsStore store = new JsonSettingsStore(file);

        store.save(new PersistedSettings(8, 2, 10, Language.SWEDISH, true));
        store.save(new PersistedSettings(1, 1, 1, Language.ENGLISH, false));

        assertEquals(new PersistedSettings(1, 1, 1, Language.ENGLISH, false), store.load());
    }
}
