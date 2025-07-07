package io.github.forest_of_dreams.data_objects.settings;

import io.github.forest_of_dreams.enums.settings.SoundType;

public class SoundSetting {
    private int Master = 5;
    private int SFX = 5;
    private int Music = 5;

    /**
     * @param volume Between 1 and 10
    * */
    public void setMasterVolume(int volume) {
        if (volume < 1 || volume > 10) return;
        Master = volume;
    }

    /**
     * @param volume Between 1 and 10
     * */
    public void setSFXVolume(int volume) {
        if (volume < 1 || volume > 10) return;
        SFX = volume;
    }

    /**
     * @param volume Between 1 and 10
     * */
    public void setMusicVolume(int volume) {
        if (volume < 1 || volume > 10) return;
        Music = volume;
    }

    public float getVolumeScale(SoundType type) {
        int volume = switch (type) {
            case SFX -> SFX;
            case Music -> Music;
        };
        float volumeScale = volume / 10f;
        float masterScale = Master / 10f;
        return volumeScale * masterScale;
    }
}
