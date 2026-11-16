package io.github.elderpath_crusade.config;

import com.badlogic.gdx.Input;
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
        PersistedSettings settings = new PersistedSettings(8, 2, 10, Language.SWEDISH, true, Input.Keys.P, Input.Keys.SPACE);

        store.save(settings);

        assertTrue(file.isFile(), "save() should create the file (and its parent dir)");
        assertEquals(settings, store.load());
    }

    @Test
    void save_overwritesRatherThanAppending(@TempDir File dir) {
        File file = new File(dir, "settings.json");
        SettingsStore store = new JsonSettingsStore(file);

        store.save(new PersistedSettings(8, 2, 10, Language.SWEDISH, true, Input.Keys.P, Input.Keys.SPACE));
        store.save(new PersistedSettings(1, 1, 1, Language.ENGLISH, false, Input.Keys.ESCAPE, Input.Keys.ENTER));

        assertEquals(new PersistedSettings(1, 1, 1, Language.ENGLISH, false, Input.Keys.ESCAPE, Input.Keys.ENTER), store.load());
    }

    @Test
    void onlineNameAndAddress_roundTrip_andAnOldFileWithoutThemStillLoads(@TempDir File dir) throws Exception {
        File file = new File(dir, "settings.json");
        SettingsStore store = new JsonSettingsStore(file);
        PersistedSettings withOnline = new PersistedSettings(8, 2, 10, Language.SWEDISH, true, Input.Keys.P, Input.Keys.SPACE,
                "Ann", "example.com:8443");
        store.save(withOnline);
        assertEquals(withOnline, store.load());

        // a file written before the online fields existed
        java.nio.file.Files.writeString(file.toPath(), "{\"masterVolume\":8,\"musicVolume\":2,\"sfxVolume\":10,\"language\":\"SWEDISH\","
                + "\"fullscreen\":true,\"pauseMenuKeycode\":" + Input.Keys.P + ",\"confirmSelectionKeycode\":" + Input.Keys.SPACE + "}");
        PersistedSettings old = store.load();
        assertEquals(8, old.masterVolume());
        assertNull(old.onlinePlayerName());
        assertNull(old.onlineServerAddress());
    }
}
