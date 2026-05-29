package io.github.elderpath_crusade.data;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PieceRegistryTest {
    private static Map<String, PieceDefinition> pieces;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadPieces() throws Exception {
        Path yamlPath = Path.of("../assets/data/pieces.yaml");
        try (InputStream is = Files.newInputStream(yamlPath)) {
            Map<String, Object> root = new Yaml().load(is);
            Map<String, Map<String, Object>> entries = (Map<String, Map<String, Object>>) root.get("pieces");
            pieces = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : entries.entrySet()) {
                String name = entry.getKey();
                Map<String, Object> v = entry.getValue();
                List<String> abilities = v.containsKey("abilities")
                        ? ((List<?>) v.get("abilities")).stream().map(Object::toString).toList()
                        : List.of();
                pieces.put(name, new PieceDefinition(
                        name,
                        (int) v.get("cost"),
                        (int) v.get("health"),
                        (int) v.get("damage"),
                        (int) v.get("speed"),
                        (int) v.get("actions"),
                        abilities
                ));
            }
        }
    }

    @Test
    void loadsAll19Pieces() {
        assertEquals(19, pieces.size());
    }

    @Test
    void allPiecesHaveValidStats() {
        for (PieceDefinition def : pieces.values()) {
            assertTrue(def.cost() >= 0, def.id() + " cost >= 0");
            assertTrue(def.health() > 0, def.id() + " health > 0");
            assertTrue(def.damage() >= 0, def.id() + " damage >= 0");
            assertTrue(def.speed() > 0, def.id() + " speed > 0");
            assertTrue(def.actions() > 0, def.id() + " actions > 0");
            assertNotNull(def.abilities(), def.id() + " abilities not null");
        }
    }

    @Test
    void wolfHasExpectedStats() {
        PieceDefinition wolf = pieces.get("Wolf");
        assertNotNull(wolf);
        assertEquals(1, wolf.cost());
        assertEquals(1, wolf.health());
        assertEquals(1, wolf.damage());
        assertEquals(1, wolf.speed());
        assertEquals(1, wolf.actions());
        assertEquals(List.of("PackHunter"), wolf.abilities());
    }

    @Test
    void kingHasExpectedStats() {
        PieceDefinition king = pieces.get("King");
        assertNotNull(king);
        assertEquals(3, king.cost());
        assertEquals(2, king.health());
        assertEquals(0, king.damage());
        assertEquals(List.of("KingEnemyAura", "KingFriendlyAura"), king.abilities());
    }

    @Test
    void crossbowmanHasExpectedStats() {
        PieceDefinition xbow = pieces.get("Crossbowman");
        assertNotNull(xbow);
        assertEquals(3, xbow.cost());
        assertEquals(1, xbow.health());
        assertEquals(2, xbow.damage());
        assertEquals(2, xbow.actions());
        assertEquals(List.of("CrossbowmanRange", "OncePerTurnAttack", "ExcessDamageCarryOver"), xbow.abilities());
    }
}
