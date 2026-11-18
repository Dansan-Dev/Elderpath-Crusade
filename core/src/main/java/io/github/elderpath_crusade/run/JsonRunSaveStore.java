package io.github.elderpath_crusade.run;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.elderpath_crusade.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * One JSON file holding the in-progress run ({@code ~/.elderpathcrusade/run.json} in the game). A plain
 * {@link File} so it is headlessly testable. Writes go to a temp file that then replaces the real one, so a
 * crash mid-save can't leave half a run. A file that can't be read, or that has another format version, is
 * never deleted: it is moved to {@code <name>.unreadable} and {@link #load()} reports "no saved run".
 */
public final class JsonRunSaveStore implements RunSaveStore {
    // The records' derived boolean methods (isEmpty()/isModified()/isTerminal()) serialize as extra properties
    // with no constructor parameter; ignore them on read (same as JsonDeckCollectionStore).
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final File file;

    public JsonRunSaveStore(File file) {
        if (file == null) throw new IllegalArgumentException("file required");
        this.file = file;
    }

    public static File defaultFile() {
        return new File(System.getProperty("user.home"), ".elderpathcrusade/run.json");
    }

    @Override
    public Optional<RunSnapshot> load() {
        if (!file.isFile()) return Optional.empty();
        try {
            RunSnapshot snapshot = MAPPER.readValue(file, RunSnapshot.class);
            if (snapshot == null || snapshot.formatVersion() != RunSnapshot.FORMAT_VERSION) {
                setAside("format version " + (snapshot == null ? "missing" : snapshot.formatVersion()));
                return Optional.empty();
            }
            return Optional.of(snapshot);
        } catch (IOException | RuntimeException e) {
            setAside(e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void save(RunSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot required");
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) parent.mkdirs();
            File tmp = new File(file.getPath() + ".tmp");
            MAPPER.writeValue(tmp, snapshot);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Losing a save must never take the running game down — the run just isn't resumable from here.
            Logger.error("RunSave", "failed to write " + file, e);
        }
    }

    @Override
    public void clear() {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            Logger.error("RunSave", "failed to delete " + file, e);
        }
    }

    private void setAside(String reason) {
        File aside = new File(file.getPath() + ".unreadable");
        try {
            Files.move(file.toPath(), aside.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Logger.log("RunSave", "saved run not usable (" + reason + "); kept as " + aside);
        } catch (IOException e) {
            Logger.error("RunSave", "saved run not usable (" + reason + ") and could not be moved aside", e);
        }
    }
}
