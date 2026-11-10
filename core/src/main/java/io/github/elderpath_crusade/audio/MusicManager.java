package io.github.elderpath_crusade.audio;
import io.github.elderpath_crusade.GameContext;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import io.github.elderpath_crusade.enums.settings.SoundType;
import io.github.elderpath_crusade.utils.Logger;

public class MusicManager {
    private static final String TAG = "MusicManager";

    private Music currentMusic = null;
    private String currentMusicPath = null;
    private boolean isMusicPlaying = false;

    private boolean isFading = false;
    private float fadeTimer = 0f;
    private float fadeDuration = 0f;
    private float fadeStartVolume = 0f;
    private float fadeTargetVolume = 0f;

    /**
     * What the manager last did and when (Sprint 64) — read by the diagnostics overlay, since
     * the user has seen the music "lag"/stop outright and this is the only way to tell, after
     * the fact, what state it was actually in and when it last changed.
     */
    public enum MusicEvent { NONE, PLAY, PAUSE, RESUME, FADE_OUT_START, FADE_IN_START, FADE_END, VOLUME_GUARDED }

    private MusicEvent lastEvent = MusicEvent.NONE;
    private long lastEventAtMillis = 0L;
    private float lastAppliedVolume = 0f;

    public MusicManager() {}

    public void update() {
        if (currentMusic == null) return;
        float settingsVolume = GameContext.get().getSettingsManager().sound.getVolumeScale(SoundType.Music);

        if (isFading) {
            fadeTimer += Gdx.graphics.getDeltaTime();
            if (fadeTimer >= fadeDuration) {
                isFading = false;
                applyVolume(fadeTargetVolume * settingsVolume);
                currentMusic.stop();
                isMusicPlaying = false;
                currentMusicPath = null;
                recordEvent(MusicEvent.FADE_END);
            } else {
                float progress = fadeTimer / fadeDuration;
                float currentVolume = fadeStartVolume + (fadeTargetVolume - fadeStartVolume) * progress;
                applyVolume(currentVolume * settingsVolume);
            }
        } else if (isMusicPlaying) {
            applyVolume(settingsVolume);
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
        applyVolume(volume);
        currentMusic.setLooping(true);
        currentMusic.play();

        isMusicPlaying = true;
        currentMusicPath = path;
        isFading = false;
        recordEvent(MusicEvent.PLAY);
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
            recordEvent(MusicEvent.PAUSE);
        }
    }

    public void resumeMusic() {
        if (isMusicNotNull() && !isMusicPlaying) {
            float volume = GameContext.get().getSettingsManager().sound.getVolumeScale(SoundType.Music);
            applyVolume(volume);
            currentMusic.play();
            isMusicPlaying = true;
            recordEvent(MusicEvent.RESUME);
        }
    }

    public void fadeOut(float durationSeconds) {
        if (isMusicNotNull() && isMusicPlaying) {
            float settingsVolume = GameContext.get().getSettingsManager().sound.getVolumeScale(SoundType.Music);
            // BUG (Sprint 64): dividing by settingsVolume blows up to Infinity/NaN once the
            // player mutes music (volume scale 0) — a NaN then propagates through update()'s
            // interpolation and gets handed to the native audio backend as the music's volume,
            // which is the likely cause of "music sometimes stops completely" (never reproduced
            // directly here — no audio device in this sandbox — but this is a real, reachable
            // bug regardless: guard it rather than leave it for the overlay to merely observe).
            fadeStartVolume = settingsVolume > 0f ? currentMusic.getVolume() / settingsVolume : 0f;
            fadeTargetVolume = 0f;
            fadeDuration = durationSeconds;
            fadeTimer = 0f;
            isFading = true;
            recordEvent(MusicEvent.FADE_OUT_START);
        }
    }

    public void fadeIn(float durationSeconds) {
        if (isMusicNotNull() && isMusicPlaying) {
            fadeStartVolume = 0f;
            fadeTargetVolume = 1f;
            fadeDuration = durationSeconds;
            fadeTimer = 0f;
            isFading = true;
            applyVolume(0f);
            recordEvent(MusicEvent.FADE_IN_START);
        }
    }

    public void crossfade(String path, float fadeOutDuration, float fadeInDuration) {
        if (isMusicNotNull() && isMusicPlaying) {
            fadeOut(fadeOutDuration);
        }
        playLoopingMusic(path);
        fadeIn(fadeInDuration);
    }

    /**
     * The single place every computed volume reaches the native {@code Music} instance — clamps
     * to a valid [0,1] range and refuses to forward a NaN/infinite value (logging it instead),
     * rather than handing the audio backend something that might silently kill playback. Also
     * where {@link #lastAppliedVolume} is updated for the diagnostics overlay.
     */
    private void applyVolume(float volume) {
        if (Float.isNaN(volume) || Float.isInfinite(volume)) {
            Logger.error(TAG, "refusing to set an invalid music volume (" + volume + "); leaving it as-is");
            recordEvent(MusicEvent.VOLUME_GUARDED);
            return;
        }
        float clamped = Math.max(0f, Math.min(1f, volume));
        currentMusic.setVolume(clamped);
        lastAppliedVolume = clamped;
    }

    private void recordEvent(MusicEvent event) {
        lastEvent = event;
        lastEventAtMillis = System.currentTimeMillis();
    }

    // --- diagnostics overlay accessors (Sprint 64) ---

    public String currentMusicPath() {
        return currentMusicPath;
    }

    public boolean isPlaying() {
        return isMusicPlaying;
    }

    public boolean isFading() {
        return isFading;
    }

    public MusicEvent lastEvent() {
        return lastEvent;
    }

    public long lastEventAtMillis() {
        return lastEventAtMillis;
    }

    public float lastAppliedVolume() {
        return lastAppliedVolume;
    }
}
