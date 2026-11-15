package io.github.elderpath_crusade.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.settings.SoundType;
import io.github.elderpath_crusade.utils.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Plays {@link SoundEffect}s (FEAT-017) using the file mapping in {@code data/sfx.yaml}.
 * Every {@link Sound} is loaded once and cached (the old {@code playSound} made a new, never
 * disposed {@code Sound} per call). Silent — never throwing — when there's no audio backend
 * (headless/tests), no yaml, or a missing file; a missing file is logged once.
 *
 * <p>The same effect can't retrigger within {@link #RETRIGGER_GAP_NANOS}: several identical
 * events in one tick (a multi-card draw, a cleave hitting three pieces) play once, not stacked.
 */
public class SoundManager {
    static final long RETRIGGER_GAP_NANOS = 60_000_000L;
    private static final String YAML_PATH = "data/sfx.yaml";

    private Map<SoundEffect, SfxRegistry.Entry> entries;
    private final Map<String, Sound> sounds = new HashMap<>();
    private final Map<SoundEffect, Long> lastPlayedNanos = new EnumMap<>(SoundEffect.class);
    private final Random random = new Random();

    public SoundManager() {}

    /** Plays one of the effect's files at the player's SFX volume × the effect's own volume. */
    public void play(SoundEffect effect) {
        if (effect == null || Gdx.audio == null || Gdx.files == null) return;
        SfxRegistry.Entry entry = entries().get(effect);
        if (entry == null) return;

        long now = System.nanoTime();
        Long last = lastPlayedNanos.get(effect);
        if (last != null && now - last < RETRIGGER_GAP_NANOS) return;
        lastPlayedNanos.put(effect, now);

        float volume = GameContext.get().getSettingsManager().sound.getVolumeScale(SoundType.SFX) * entry.volume();
        if (volume <= 0f) return;
        Sound sound = sound(entry.files().get(random.nextInt(entry.files().size())));
        if (sound != null) sound.play(volume);
    }

    /** Loads every mapped file now (from LoadingRoom) so the first play of each doesn't hitch. */
    public void preload() {
        if (Gdx.audio == null || Gdx.files == null) return;
        for (SfxRegistry.Entry entry : entries().values()) {
            for (String file : entry.files()) sound(file);
        }
    }

    public void dispose() {
        for (Sound sound : sounds.values()) if (sound != null) sound.dispose();
        sounds.clear();
    }

    private Sound sound(String file) {
        if (sounds.containsKey(file)) return sounds.get(file);
        Sound sound = null;
        FileHandle handle = Gdx.files.internal("audio/" + file);
        if (handle.exists()) {
            try {
                sound = Gdx.audio.newSound(handle);
            } catch (RuntimeException e) {
                Logger.error("sfx", "could not load audio/" + file + ": " + e.getMessage());
            }
        } else {
            Logger.log("sfx", "missing sound file, skipped: audio/" + file);
        }
        sounds.put(file, sound);
        return sound;
    }

    private Map<SoundEffect, SfxRegistry.Entry> entries() {
        if (entries == null) {
            FileHandle yaml = Gdx.files.internal(YAML_PATH);
            List<String> unknown = new ArrayList<>();
            entries = yaml.exists() ? SfxRegistry.parse(yaml.readString(), unknown) : Map.of();
            if (!yaml.exists()) Logger.log("sfx", "no " + YAML_PATH + " — sound effects off");
            if (!unknown.isEmpty()) Logger.log("sfx", "unknown effects in " + YAML_PATH + ": " + unknown);
        }
        return entries;
    }
}
