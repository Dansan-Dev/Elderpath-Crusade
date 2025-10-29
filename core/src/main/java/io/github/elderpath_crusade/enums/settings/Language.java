package io.github.elderpath_crusade.enums.settings;

public enum Language {
    ENGLISH("language/english/"), SWEDISH("language/swedish/");

    public final String path;

    private Language(String path) {
        this.path = path;
    }
}
