package io.github.elderpath_crusade.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.elderpath_crusade.data_objects.settings.PersistedSettings;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Single-object JSON-file-backed {@link SettingsStore} — same pattern as {@code
 * collection/JsonDeckCollectionStore} (plain {@link File}, not a Gdx path, so it's headlessly
 * testable with a temp file), just one object instead of an append-only array.
 */
public final class JsonSettingsStore implements SettingsStore {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final File file;

    public JsonSettingsStore(File file) {
        if (file == null) throw new IllegalArgumentException("file required");
        this.file = file;
    }

    @Override
    public PersistedSettings load() {
        if (!file.isFile()) return PersistedSettings.DEFAULTS;
        try {
            return MAPPER.readValue(file, PersistedSettings.class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read settings from " + file, e);
        }
    }

    @Override
    public void save(PersistedSettings settings) {
        if (settings == null) throw new IllegalArgumentException("settings required");
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            MAPPER.writeValue(file, settings);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write settings to " + file, e);
        }
    }
}
