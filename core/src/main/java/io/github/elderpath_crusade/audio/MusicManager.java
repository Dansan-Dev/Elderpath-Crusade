package io.github.elderpath_crusade.audio;
import io.github.elderpath_crusade.GameContext;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import io.github.elderpath_crusade.enums.settings.SoundType;

public class MusicManager {
    private Music currentMusic = null;
    private String currentMusicPath = null;
    private boolean isMusicPlaying = false;

    private boolean isFading = false;
    private float fadeTimer = 0f;
    private float fadeDuration = 0f;
    private float fadeStartVolume = 0f;
    private float fadeTargetVolume = 0f;

    public MusicManager() {}

    public void update() {
        if (currentMusic == null) return;
        float settingsVolume = GameContext.get().getSettingsManager().sound.getVolumeScale(SoundType.Music);

        if (isFading) {
            fadeTimer += Gdx.graphics.getDeltaTime();
            if (fadeTimer >= fadeDuration) {
                isFading = false;
                currentMusic.setVolume(fadeTargetVolume * settingsVolume);
                currentMusic.stop();
                isMusicPlaying = false;
                currentMusicPath = null;
            } else {
                float progress = fadeTimer / fadeDuration;
                float currentVolume = fadeStartVolume + (fadeTargetVolume - fadeStartVolume) * progress;
                currentMusic.setVolume(currentVolume * settingsVolume);
            }
        } else if (isMusicPlaying) {
            currentMusic.setVolume(settingsVolume);
        }
    }

    public void playLoopingMusic(String path) {
        if (path.equals(currentMusicPath)) {
            if (!isMusicPlaying) resumeMusic();
            return;
        }

        if (isMusicNotNull()) {
            dispose();
        }

        currentMusic = Gdx.audio.newMusic(Gdx.files.internal("music/" + path));
        float volume = GameContext.get().getSettingsManager().sound.getVolumeScale(SoundType.Music);
        currentMusic.setVolume(volume);
        currentMusic.setLooping(true);
        currentMusic.play();

        isMusicPlaying = true;
        currentMusicPath = path;
        isFading = false;
    }

    private boolean isMusicNotNull() {
        return currentMusic != null;
    }

    private void dispose() {
        currentMusic.stop();
        currentMusic.dispose();
    }

    public void pauseMusic() {
        if (currentMusic != null && isMusicPlaying) {
            currentMusic.pause();
            isMusicPlaying = false;
            isFading = false;
        }
    }

    public void resumeMusic() {
        if (isMusicNotNull() && !isMusicPlaying) {
            float volume = GameContext.get().getSettingsManager().sound.getVolumeScale(SoundType.Music);
            currentMusic.setVolume(volume);
            currentMusic.play();
            isMusicPlaying = true;
        }
    }

    public void fadeOut(float durationSeconds) {
        if (isMusicNotNull() && isMusicPlaying) {
            float settingsVolume = GameContext.get().getSettingsManager().sound.getVolumeScale(SoundType.Music);
            fadeStartVolume = currentMusic.getVolume() / settingsVolume;
            fadeTargetVolume = 0f;
            fadeDuration = durationSeconds;
            fadeTimer = 0f;
            isFading = true;
        }
    }

    public void fadeIn(float durationSeconds) {
        if (isMusicNotNull() && isMusicPlaying) {
            fadeStartVolume = 0f;
            fadeTargetVolume = 1f;
            fadeDuration = durationSeconds;
            fadeTimer = 0f;
            isFading = true;
            currentMusic.setVolume(0f);
        }
    }

    public void crossfade(String path, float fadeOutDuration, float fadeInDuration) {
        if (isMusicNotNull() && isMusicPlaying) {
            fadeOut(fadeOutDuration);
        }
        playLoopingMusic(path);
        fadeIn(fadeInDuration);
    }
}
