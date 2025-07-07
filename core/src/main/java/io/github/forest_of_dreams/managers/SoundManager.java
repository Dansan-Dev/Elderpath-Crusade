package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import io.github.forest_of_dreams.enums.settings.SoundType;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class SoundManager {
    private static Music currentMusic = null;
    private static final List<Music> musicQueue = new ArrayList<>();

    @Setter
    private static boolean isMusicPlaying = false;
    @Setter
    private static boolean isFadingOut = false;
    @Setter
    private static boolean isFadingIn = false;
    @Setter
    private static boolean immediateTransition = false;
    @Setter
    private static boolean isTransitioning = false;
    @Setter
    private static boolean pauseOnCompletion = false;

    private static float fadeOutTime = 0; // Milliseconds
    private static float fadeOutScale = 1f;

    private static float fadeInTime = 0;
    private static float fadeInScale = 0;

    private static final Music.OnCompletionListener completionListener = new Music.OnCompletionListener() {
        @Override
        public void onCompletion(Music music) {
            cleanupTrack();
            if (!musicQueue.isEmpty()) {
                currentMusic = musicQueue.remove(0);
                if (!pauseOnCompletion) {
                    startTrack();
                    setPauseOnCompletion(false);
                }
            } else {
                currentMusic = null;
            }
        }
    };


    public static void update() {
        System.out.println("I AM HERE");

        float musicVolume = SettingsManager.sound.getVolumeScale(SoundType.Music);
        if (isTransitioning) {
            float timeDelta = (Gdx.graphics.getDeltaTime() * SettingsManager.getFPS());

            if (immediateTransition) {
                setImmediateTransition(false);
                if (currentMusic != null) cleanupTrack();

                currentMusic = musicQueue.remove(0);
                currentMusic.setVolume(musicVolume);
                currentMusic.play();
            } else if (isFadingOut) {
                fadeOutScale -= (fadeOutScale / fadeOutTime) * timeDelta;
                currentMusic.setVolume(fadeOutScale * musicVolume);

                if (fadeOutScale <= 0) {
                    cleanupTrack();

                    fadeOutScale = 1f;
                    fadeOutTime = 0;

                    setFadingOut(false);
                    if (!musicQueue.isEmpty()) {
                        setFadingIn(true);
                        fadeInScale = 0;
                        currentMusic = musicQueue.remove(0);
                        currentMusic.setVolume(fadeInScale);
                        currentMusic.play();
                    } else {
                        setTransitioning(false);
                    }
                }
            } else if (isFadingIn) {
                fadeInScale += (fadeInScale / fadeInTime) * timeDelta;

                currentMusic.setVolume(fadeInScale * musicVolume);

                if (fadeInScale >= 1) {
                    currentMusic.setVolume(musicVolume);
                    fadeInScale = 0;
                    fadeInTime = 0;

                    setFadingIn(false);
                    setTransitioning(false);
                }
            } else {
                setTransitioning(false);
            }
        } else {
            if (isMusicPlaying) currentMusic.setVolume(musicVolume);
        }
    }

    public static Sound playSound(String path) {
        float volume = SettingsManager.sound.getVolumeScale(SoundType.SFX);
        Sound sound = Gdx.audio.newSound(Gdx.files.internal("audio/" + path));
        sound.play(volume);
        return sound;
    }

    public static void queueMusic(String path) {
        float volume = SettingsManager.sound.getVolumeScale(SoundType.Music);
        Music music = Gdx.audio.newMusic(Gdx.files.internal("music/" + path));
        music.setVolume(volume);
        musicQueue.add(music);
    }

    /**
     *
     * @param fadeOut time in milliseconds
     * @param fadeIn time in milliseconds
     */
    public static void fadeTransition(int fadeOut, int fadeIn) {
        fadeOutTime = fadeOut;
        fadeInTime = fadeIn;
        if (isMusicPlaying) {
            setTransitioning(true);
            setFadingOut(true);
        }
    }

    public static void transition() {
        setTransitioning(true);
        setImmediateTransition(true);
    }

    private static void cleanupTrack() {
        currentMusic.stop();
        currentMusic.dispose();
    }

    private static void pauseTrack() {
        setMusicPlaying(false);
        currentMusic.pause();
    }

    public static void startTrack() {
        setMusicPlaying(true);
        float musicVolume = SettingsManager.sound.getVolumeScale(SoundType.Music);
        currentMusic.setVolume(musicVolume);
        currentMusic.play();
    }
}
