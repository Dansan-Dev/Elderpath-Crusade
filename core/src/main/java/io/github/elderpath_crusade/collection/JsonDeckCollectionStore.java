package io.github.elderpath_crusade.collection;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only JSON-file-backed {@link DeckCollectionStore} — one JSON array of {@link
 * DeckCollectionEntry}. Takes a plain {@link File}, not a Gdx path, so it's headlessly
 * testable (a temp file) exactly like {@code match/}'s pure logic; {@link CollectionManager}
 * resolves the real production path.
 */
public final class JsonDeckCollectionStore implements DeckCollectionStore {
    // RunCardModification/RunCardInstance each have a derived boolean method (isEmpty()/isModified())
    // that Jackson's record support picks up as an extra serialized "property" with no matching
    // constructor parameter — harmless on write, but FAIL_ON_UNKNOWN_PROPERTIES rejects it back on
    // read. Disabling that check here (not touching those domain records just for this) is the fix.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final File file;

    public JsonDeckCollectionStore(File file) {
        if (file == null) throw new IllegalArgumentException("file required");
        this.file = file;
    }

    @Override
    public List<DeckCollectionEntry> loadAll() {
        if (!file.isFile()) return new ArrayList<>();
        try {
            DeckCollectionEntry[] entries = MAPPER.readValue(file, DeckCollectionEntry[].class);
            List<DeckCollectionEntry> out = new ArrayList<>(entries.length);
            for (DeckCollectionEntry entry : entries) out.add(entry);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read deck collection from " + file, e);
        }
    }

    @Override
    public void save(DeckCollectionEntry entry) {
        if (entry == null) throw new IllegalArgumentException("entry required");
        List<DeckCollectionEntry> all = loadAll();
        all.add(entry);
        write(all);
    }

    private void write(List<DeckCollectionEntry> entries) {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            MAPPER.writeValue(file, entries);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write deck collection to " + file, e);
        }
    }
}
