package io.github.forest_of_dreams.data_objects.settings;
import org.yaml.snakeyaml.Yaml;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.forest_of_dreams.enums.settings.Language;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LanguageSetting {
    @Getter @Setter
    private Language language = Language.ENGLISH;


    public LanguageSetting() {}

    public void initialize() {}

    /**
    * @param fileName shouldn't contain .yml
    * */
    public Object getFromFile(String fileName, List<String> key_path) {
        FileHandle file = Gdx.files.internal(language.path + fileName + ".yml");
        Yaml yaml = new Yaml();
        String fileContent = file.readString();
        Map<String, Object> data = yaml.load(fileContent);

        List<String> keys = new ArrayList<>(key_path);
        Object value = data.get(keys.remove(0));
        while (true) {
            if (value == null) return "[MISSING TEXT]";
            if (keys.isEmpty()) return value;
            if (value instanceof Map<?, ?>)
                value = ((Map<String, Object>) value).get(keys.remove(0));
        }
    }

    public String getDirectFromFile(String fileName, String key) {
        FileHandle file = Gdx.files.internal(language.path + fileName + ".yml");
        Yaml yaml = new Yaml();
        String fileContent = file.readString();
        Map<String, Object> data = yaml.load(fileContent);

        Object value = data.get(key);

        if (!(value instanceof String)) return "[MISSING TEXT]";
        else return value.toString();
    }

    public Map<String, Object> getFileContent(String fileName) {
        FileHandle file = Gdx.files.internal(language.path + fileName + ".yml");
        Yaml yaml = new Yaml();
        String fileContent = file.readString();
        return yaml.load(fileContent);
    }
}
