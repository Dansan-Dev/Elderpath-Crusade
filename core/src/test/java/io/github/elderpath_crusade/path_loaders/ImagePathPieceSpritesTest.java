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

import static org.junit.jupiter.api.Assertions.*;

class ImagePathPieceSpritesTest {

    @Test
    void forRegistryKey_knownKey_returnsItsArt() {
        assertEquals(ImagePathPieceSprites.WOLF_CUB, ImagePathPieceSprites.forRegistryKey("WolfCub"));
        assertEquals("images/pieces/WolfCub.png", ImagePathPieceSprites.WOLF_CUB.getPath());
    }

    @Test
    void forRegistryKey_runModifiedSyntheticKey_usesBasePieceArt() {
        assertEquals(ImagePathPieceSprites.CROW, ImagePathPieceSprites.forRegistryKey("Crow#run-6"));
        assertEquals(ImagePathPieceSprites.WOLF_CUB, ImagePathPieceSprites.forRegistryKey("WolfCub#run-3"));
    }

    @Test
    void forRegistryKey_unknownOrNull_returnsNull() {
        assertNull(ImagePathPieceSprites.forRegistryKey("NoSuchPiece"));
        assertNull(ImagePathPieceSprites.forRegistryKey(null));
    }

    /** Every piece in pieces.yaml has art, and every art file exists at the native size the sprite code crops. */
    @Nested
    @RequiresAssets
    class AgainstAssets {
        @Test
        @SuppressWarnings("unchecked")
        void everyPieceHasArt() throws Exception {
            try (InputStream is = Files.newInputStream(Path.of("../assets/data/pieces.yaml"))) {
                Map<String, Object> root = new Yaml().load(is);
                for (String key : ((Map<String, Object>) root.get("pieces")).keySet()) {
                    assertNotNull(ImagePathPieceSprites.forRegistryKey(key), "no figure art constant for " + key);
                }
            }
        }

        @Test
        void everyArtFileExistsAtNativeSize() throws Exception {
            for (ImagePathPieceSprites sprite : ImagePathPieceSprites.values()) {
                Path file = Path.of("../assets", sprite.getPath());
                assertTrue(Files.exists(file), "missing " + file);
                BufferedImage image = ImageIO.read(file.toFile());
                assertEquals(ImagePathPieceSprites.SIZE, image.getWidth(), file + " width");
                assertEquals(ImagePathPieceSprites.SIZE, image.getHeight(), file + " height");
            }
        }
    }
}
