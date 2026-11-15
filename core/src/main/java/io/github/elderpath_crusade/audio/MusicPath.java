package io.github.elderpath_crusade.audio;

/** Every music track the game plays; paths are relative to {@code assets/music/}. */
public enum MusicPath {
    EVENING_HARMONY("Evening_Harmony.mp3"),
    BATTLE("Daniel_Game.mp3");

    private final String fileName;

    MusicPath(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
