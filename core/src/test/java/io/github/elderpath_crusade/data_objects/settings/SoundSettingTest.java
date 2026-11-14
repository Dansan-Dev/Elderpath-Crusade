package io.github.elderpath_crusade.data_objects.settings;

import io.github.elderpath_crusade.enums.settings.SoundType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoundSettingTest {

    @Test
    void defaults_areFiveAcrossTheBoard() {
        SoundSetting sound = new SoundSetting();
        assertEquals(5, sound.getMasterVolume());
        assertEquals(5, sound.getMusicVolume());
        assertEquals(5, sound.getSFXVolume());
    }

    @Test
    void setters_areReflectedByTheirGetters() {
        SoundSetting sound = new SoundSetting();
        sound.setMasterVolume(8);
        sound.setMusicVolume(2);
        sound.setSFXVolume(10);
        assertEquals(8, sound.getMasterVolume());
        assertEquals(2, sound.getMusicVolume());
        assertEquals(10, sound.getSFXVolume());
    }

    @Test
    void setters_rejectOutOfRangeValues_leavingThePreviousValueInPlace() {
        SoundSetting sound = new SoundSetting();
        sound.setMasterVolume(0);
        sound.setMasterVolume(11);
        assertEquals(5, sound.getMasterVolume(), "out-of-range values are no-ops, not clamped");
    }

    @Test
    void getVolumeScale_multipliesMasterAndTypeSpecificScales() {
        SoundSetting sound = new SoundSetting();
        sound.setMasterVolume(10);
        sound.setMusicVolume(5);
        assertEquals(0.5f, sound.getVolumeScale(SoundType.Music), 0.0001f);
    }
}
