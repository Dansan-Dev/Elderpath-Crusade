package io.github.elderpath_crusade.audio;
import io.github.elderpath_crusade.GameContext;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import io.github.elderpath_crusade.enums.settings.SoundType;

public class SoundManager {
    public SoundManager() {}

    public Sound playSound(String path) {
        float volume = GameContext.get().getSettingsManager().sound.getVolumeScale(SoundType.SFX);
        Sound sound = Gdx.audio.newSound(Gdx.files.internal("audio/" + path));
        sound.play(volume);
        return sound;
    }
}
