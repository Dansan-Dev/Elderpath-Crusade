package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import io.github.elderpath_crusade.enums.settings.SoundType;

public class SoundManager {
    public static Sound playSound(String path) {
        float volume = SettingsManager.sound.getVolumeScale(SoundType.SFX);
        Sound sound = Gdx.audio.newSound(Gdx.files.internal("audio/" + path));
        sound.play(volume);
        return sound;
    }
}
