package io.github.elderpath_crusade.path_loaders;

import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** ART-003/004/005: relic icons, spell portraits and the UI textures. */
class ArtPathLoadersTest {

    @Test
    void relicIcons_lookupByIdOrNull() {
        assertEquals(ImagePathRelicIcons.HORN, ImagePathRelicIcons.forRelicId("Horn"));
        assertEquals("images/relics/Horn.png", ImagePathRelicIcons.HORN.getPath());
        assertNull(ImagePathRelicIcons.forRelicId("NoSuchRelic"));
        assertNull(ImagePathRelicIcons.forRelicId(null));
    }

    @Test
    void spellArt_lookupByIdDisplayNameOrSyntheticName() {
        assertEquals(ImagePathSpellArt.FIREBALL, ImagePathSpellArt.forSpell("Fireball"));
        assertEquals(ImagePathSpellArt.BLIZZARD, ImagePathSpellArt.forSpell("Blizzard#run-4"));
        assertEquals(ImagePathSpellArt.HEALING_LIGHT, ImagePathSpellArt.forSpell("Healing Light"));
        assertEquals("images/spells/Frostbolt.png", ImagePathSpellArt.FROSTBOLT.getPath());
        assertNull(ImagePathSpellArt.forSpell("Wolf"));
        assertNull(ImagePathSpellArt.forSpell(null));
    }

    @Nested
    @RequiresAssets
    class AgainstAssets {
        @Test
        void everyRelicHasAnIcon() throws Exception {
            for (String id : yamlKeys("relics.yaml", "relics")) {
                assertNotNull(ImagePathRelicIcons.forRelicId(id), "no icon constant for relic " + id);
            }
        }

        @Test
        void everySpellHasArt() throws Exception {
            for (String id : yamlKeys("spells.yaml", "spells")) {
                assertNotNull(ImagePathSpellArt.forSpell(id), "no art constant for spell " + id);
            }
        }

        @Test
        void iconFilesExistAtNativeSize() throws Exception {
            for (ImagePathRelicIcons icon : ImagePathRelicIcons.values()) assertSize(icon.getPath(), 64, 64);
            for (ImagePathSpellArt art : ImagePathSpellArt.values()) assertSize(art.getPath(), 64, 64);
        }

        @Test
        void uiTexturesExist() throws Exception {
            assertSize(ImagePathSpritesAndAnimations.PLOT_GRASS.getPath(), 64, 64);
            assertSize(ImagePathSpritesAndAnimations.PLOT_DIRT.getPath(), 64, 32);
            assertEquals(5, ImagePathBackgroundAndUI.BUTTON_PLATES.size());
            for (ImagePathBackgroundAndUI variant : ImagePathBackgroundAndUI.BUTTON_PLATES) {
                BufferedImage plate = read(variant.getPath());
                // Button's nine-patch insets (5 px each side) need room for a middle
                assertTrue(plate.getWidth() > 10 && plate.getHeight() > 10, variant + " too small for its nine-patch insets");
            }
            assertSize(ImagePathSpritesAndAnimations.MOUNTAIN_TERRAIN.getPath(), 1024, 1024); // MountainSprite's crop
        }

        @SuppressWarnings("unchecked")
        private Set<String> yamlKeys(String file, String root) throws Exception {
            try (InputStream is = Files.newInputStream(Path.of("../assets/data", file))) {
                Map<String, Object> yaml = new Yaml().load(is);
                return ((Map<String, Object>) yaml.get(root)).keySet();
            }
        }

        private void assertSize(String path, int width, int height) throws Exception {
            BufferedImage image = read(path);
            assertEquals(width, image.getWidth(), path + " width");
            assertEquals(height, image.getHeight(), path + " height");
        }

        private BufferedImage read(String path) throws Exception {
            Path file = Path.of("../assets", path);
            assertTrue(Files.exists(file), "missing " + file);
            return ImageIO.read(file.toFile());
        }
    }
}
