package io.github.elderpath_crusade.audio;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code assets/data/sfx.yaml} (FEAT-017): each {@link SoundEffect} name maps to one or
 * more candidate files (relative to {@code assets/audio/}, one picked at random per play) and a
 * volume multiplier on top of the player's SFX volume setting.
 *
 * <pre>
 * sfx:
 *   ATTACK: {volume: 0.8, files: [kenney_impact-sounds/Audio/impactPunch_medium_000.ogg, ...]}
 * </pre>
 *
 * Unknown names are ignored (logged by the caller), so a yaml written for a newer build never
 * breaks an older one. Pure — no Gdx — so it is tested from a string.
 */
public final class SfxRegistry {
    /** One effect's candidate files and its volume multiplier (0..1). */
    public record Entry(List<String> files, float volume) {
        public Entry {
            files = List.copyOf(files);
        }
    }

    private SfxRegistry() {
    }

    /** Parses the yaml text; returns the entries it defines and lists unknown names in {@code unknown}. */
    @SuppressWarnings("unchecked")
    public static Map<SoundEffect, Entry> parse(String yamlText, List<String> unknown) {
        Map<SoundEffect, Entry> out = new EnumMap<>(SoundEffect.class);
        if (yamlText == null || yamlText.isBlank()) return out;
        Object root = new Yaml().load(yamlText);
        if (!(root instanceof Map<?, ?> rootMap) || !(rootMap.get("sfx") instanceof Map<?, ?> sfx)) return out;
        for (Map.Entry<?, ?> e : sfx.entrySet()) {
            String name = String.valueOf(e.getKey());
            SoundEffect effect;
            try {
                effect = SoundEffect.valueOf(name);
            } catch (IllegalArgumentException ex) {
                if (unknown != null) unknown.add(name);
                continue;
            }
            if (!(e.getValue() instanceof Map<?, ?> body)) continue;
            List<String> files = new ArrayList<>();
            if (body.get("files") instanceof List<?> list) {
                for (Object f : list) if (f != null && !String.valueOf(f).isBlank()) files.add(String.valueOf(f));
            }
            float volume = 1f;
            if (body.get("volume") instanceof Number n) volume = Math.max(0f, Math.min(1f, n.floatValue()));
            if (!files.isEmpty()) out.put(effect, new Entry(files, volume));
        }
        return Collections.unmodifiableMap(out);
    }
}
