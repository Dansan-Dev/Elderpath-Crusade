package io.github.elderpath_crusade.config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class InfoDataManager {
    private static final String INFO_FILE = "info.yml";
    private Map<String, Object> data;

    public InfoDataManager() {}

    private void ensureLoaded() {
        if (data != null) return;
        try {
            FileHandle file = Gdx.files.internal(INFO_FILE);
            if (file.exists()) {
                data = new Yaml().load(file.readString());
            } else {
                data = Collections.emptyMap();
            }
        } catch (Exception e) {
            Gdx.app.error("InfoDataManager", "Error loading " + INFO_FILE, e);
            data = Collections.emptyMap();
        }
    }

    public String getTitle(String category) {
        ensureLoaded();
        Map<String, Object> categoryData = getCategoryData(category);
        Object title = categoryData.get("title");
        return title != null ? title.toString() : category;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getEntries(String category) {
        ensureLoaded();
        Map<String, Object> categoryData = getCategoryData(category);
        Object entries = categoryData.get("entries");
        if (entries instanceof List) {
            return (List<Map<String, String>>) entries;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getCategoryData(String category) {
        if (data != null && data.containsKey(category)) {
            Object catData = data.get(category);
            if (catData instanceof Map) {
                return (Map<String, Object>) catData;
            }
        }
        return Collections.emptyMap();
    }

    public String getRawFileContent(String fileName) {
        try {
            FileHandle file = Gdx.files.internal(fileName);
            if (file.exists()) {
                return file.readString();
            }
        } catch (Exception e) {
            Gdx.app.error("InfoDataManager", "Error reading raw file " + fileName, e);
        }
        return "[ERROR LOADING CONTENT]";
    }
}
