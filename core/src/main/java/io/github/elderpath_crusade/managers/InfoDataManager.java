package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class InfoDataManager {
    private static final String INFO_FILE = "info.yml";
    private static Map<String, Object> data;

    static {
        loadData();
    }

    private static void loadData() {
        try {
            FileHandle file = Gdx.files.internal(INFO_FILE);
            if (file.exists()) {
                Yaml yaml = new Yaml();
                data = yaml.load(file.readString());
            } else {
                data = Collections.emptyMap();
            }
        } catch (Exception e) {
            Gdx.app.error("InfoDataManager", "Error loading " + INFO_FILE, e);
            data = Collections.emptyMap();
        }
    }

    public static String getTitle(String category) {
        Map<String, Object> categoryData = getCategoryData(category);
        Object title = categoryData.get("title");
        return title != null ? title.toString() : category;
    }

    public static List<Map<String, String>> getEntries(String category) {
        Map<String, Object> categoryData = getCategoryData(category);
        Object entries = categoryData.get("entries");
        if (entries instanceof List) {
            return (List<Map<String, String>>) entries;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getCategoryData(String category) {
        if (data != null && data.containsKey(category)) {
            Object catData = data.get(category);
            if (catData instanceof Map) {
                return (Map<String, Object>) catData;
            }
        }
        return Collections.emptyMap();
    }

    public static String getRawFileContent(String fileName) {
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
