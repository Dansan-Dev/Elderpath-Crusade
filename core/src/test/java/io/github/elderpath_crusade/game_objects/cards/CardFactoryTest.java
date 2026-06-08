package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@RequiresAssets
class CardFactoryTest {
    private static Set<String> registryKeys;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadRegistryKeys() throws Exception {
        Path yamlPath = Path.of("../assets/data/pieces.yaml");
        try (InputStream is = Files.newInputStream(yamlPath)) {
            Map<String, Object> root = new Yaml().load(is);
            Map<String, Map<String, Object>> entries = (Map<String, Map<String, Object>>) root.get("pieces");
            registryKeys = entries.keySet();
        }
    }

    @Test
    void allSummonCardNamesMapToRegistryKeys() {
        List<String> summonCardNames = List.of(
            "Wolf", "Wolf Cub", "Rogue", "Fairy", "Wind Spirit", "Big Toad",
            "Sniper", "Barbarian", "King", "Charger", "Crossbowman",
            "Skeleton Bomber", "Warp Mage", "Commander", "Hero",
            "Storm Mage", "Rifleman", "Crow", "Shockling"
        );

        for (String displayName : summonCardNames) {
            String key = PieceRegistry.toRegistryKey(displayName);
            assertTrue(registryKeys.contains(key),
                "Card '" + displayName + "' maps to key '" + key + "' which is not in pieces.yaml");
        }
    }

    @Test
    void toRegistryKeyStripsSpaces() {
        assertEquals("WolfCub", PieceRegistry.toRegistryKey("Wolf Cub"));
        assertEquals("BigToad", PieceRegistry.toRegistryKey("Big Toad"));
        assertEquals("WindSpirit", PieceRegistry.toRegistryKey("Wind Spirit"));
        assertEquals("StormMage", PieceRegistry.toRegistryKey("Storm Mage"));
        assertEquals("SkeletonBomber", PieceRegistry.toRegistryKey("Skeleton Bomber"));
        assertEquals("WarpMage", PieceRegistry.toRegistryKey("Warp Mage"));
    }

    @Test
    void registryHas19Pieces() {
        assertEquals(19, registryKeys.size());
    }
}
