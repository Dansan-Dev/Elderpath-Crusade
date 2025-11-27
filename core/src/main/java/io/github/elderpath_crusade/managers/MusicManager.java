package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import io.github.elderpath_crusade.enums.settings.SoundType;

public class MusicManager {
    private static Music currentMusic = null;
    private static String currentMusicPath = null;
    private static boolean isMusicPlaying = false;

    // Fade state
    private static boolean isFading = false;
    private static float fadeTimer = 0f;
    private static float fadeDuration = 0f;
    private static float fadeStartVolume = 0f;
    private static float fadeTargetVolume = 0f;


    /**
     * Update music volume and handle active fades.
     * Should be called each frame.
     */
    public static void update() {
        if (currentMusic == null) return;
        float settingsVolume = SettingsManager.sound.getVolumeScale(SoundType.Music);

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

    /**
     * Play a looping music track.
     * If the same track is already playing, it will continue without restarting.
     * If different music is playing, it will be stopped and replaced.
     * @param path path to the music file (relative to music/ directory)
     */
    public static void playLoopingMusic(String path) {
        if (path.equals(currentMusicPath)) {
            if (!isMusicPlaying) resumeMusic();
            return;
        }

        if (isMusicNotNull()) {
            dispose();
        }

        currentMusic = Gdx.audio.newMusic(Gdx.files.internal("music/" + path));

        float volume = SettingsManager.sound.getVolumeScale(SoundType.Music);
        currentMusic.setVolume(volume);

        currentMusic.setLooping(true);
        currentMusic.play();

        isMusicPlaying = true;
        currentMusicPath = path;
        isFading = false;
    }

    private static boolean isMusicNotNull() {
        return currentMusic != null;
    }

    private static void dispose() {
        currentMusic.stop();
        currentMusic.dispose();
    }

    /**
     * Pause the currently playing music.
     */
    public static void pauseMusic() {
        if (currentMusic != null && isMusicPlaying) {
            currentMusic.pause();
            isMusicPlaying = false;
            isFading = false;
        }
    }

    /**
     * Resume the currently paused music.
     */
    public static void resumeMusic() {
        if (isMusicNotNull() && !isMusicPlaying) {
            float volume = SettingsManager.sound.getVolumeScale(SoundType.Music);
            currentMusic.setVolume(volume);
            currentMusic.play();
            isMusicPlaying = true;
        }
    }

    /**
     * Fade out the currently playing music to silence.
     * @param durationSeconds how long the fade should take (in seconds)
     */
    public static void fadeOut(float durationSeconds) {
        if (isMusicNotNull() && isMusicPlaying) {
            float settingsVolume = SettingsManager.sound.getVolumeScale(SoundType.Music);
            fadeStartVolume = currentMusic.getVolume() / settingsVolume; // Normalize to 0-1
            fadeTargetVolume = 0f;
            fadeDuration = durationSeconds;
            fadeTimer = 0f;
            isFading = true;
        }
    }

    /**
     * Fade in the currently playing music from silence to full volume.
     * @param durationSeconds how long the fade should take (in seconds)
     */
    public static void fadeIn(float durationSeconds) {
        if (isMusicNotNull() && isMusicPlaying) {
            fadeStartVolume = 0f;
            fadeTargetVolume = 1f;
            fadeDuration = durationSeconds;
            fadeTimer = 0f;
            isFading = true;
            currentMusic.setVolume(0f); // Start at silence
        }
    }

    /**
     * Fade out current music and start new music with fade in.
     * @param path path to the new music file (relative to music/ directory)
     * @param fadeOutDuration fade out duration in seconds
     * @param fadeInDuration fade in duration in seconds
     */
    public static void crossfade(String path, float fadeOutDuration, float fadeInDuration) {
        if (isMusicNotNull() && isMusicPlaying) {
            fadeOut(fadeOutDuration);
        }
        playLoopingMusic(path);
        fadeIn(fadeInDuration);
    }
}
