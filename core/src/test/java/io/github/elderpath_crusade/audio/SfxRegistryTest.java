package io.github.elderpath_crusade.audio;

import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** FEAT-017: parsing data/sfx.yaml. */
class SfxRegistryTest {

    @Test
    void parsesFilesAndVolume_clampsVolume_defaultsVolumeToOne() {
        Map<SoundEffect, SfxRegistry.Entry> entries = SfxRegistry.parse("""
                sfx:
                  ATTACK: {volume: 0.8, files: [a.ogg, b.ogg]}
                  MOVE: {files: [step.ogg]}
                  DEATH: {volume: 7, files: [d.ogg]}
                """, null);

        assertEquals(new SfxRegistry.Entry(List.of("a.ogg", "b.ogg"), 0.8f), entries.get(SoundEffect.ATTACK));
        assertEquals(1f, entries.get(SoundEffect.MOVE).volume());
        assertEquals(1f, entries.get(SoundEffect.DEATH).volume(), "volume is clamped to 0..1");
    }

    @Test
    void unknownNamesAreReported_entriesWithoutFilesAreDropped() {
        List<String> unknown = new ArrayList<>();
        Map<SoundEffect, SfxRegistry.Entry> entries = SfxRegistry.parse("""
                sfx:
                  NOT_A_SOUND: {files: [x.ogg]}
                  HEAL: {volume: 0.5, files: []}
                """, unknown);

        assertEquals(List.of("NOT_A_SOUND"), unknown);
        assertTrue(entries.isEmpty());
    }

    @Test
    void emptyOrMalformedYaml_isNoSoundsNotAnError() {
        assertTrue(SfxRegistry.parse("", null).isEmpty());
        assertTrue(SfxRegistry.parse(null, null).isEmpty());
        assertTrue(SfxRegistry.parse("something: else", null).isEmpty());
    }

    @Nested
    @RequiresAssets
    class AgainstAssets {
        @Test
        void realYaml_coversEverySoundEffect_withExistingFiles() throws Exception {
            List<String> unknown = new ArrayList<>();
            Map<SoundEffect, SfxRegistry.Entry> entries =
                    SfxRegistry.parse(Files.readString(Path.of("../assets/data/sfx.yaml")), unknown);

            assertEquals(List.of(), unknown, "names in sfx.yaml that no SoundEffect matches");
            for (SoundEffect effect : SoundEffect.values()) {
                SfxRegistry.Entry entry = entries.get(effect);
                assertNotNull(entry, "no sfx.yaml entry for " + effect);
                for (String file : entry.files()) {
                    assertTrue(Files.exists(Path.of("../assets/audio", file)), effect + ": missing audio/" + file);
                }
            }
        }
    }
}
