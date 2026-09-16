package io.github.elderpath_crusade.data;

import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for spells.yaml: mirrors PieceRegistryTest's pattern of parsing
 * the real asset file directly (bypassing Gdx.files, unavailable in plain JUnit) to
 * confirm the schema is valid and the ported spells kept their original mana costs.
 */
@RequiresAssets
class SpellRegistryTest {
    private static Map<String, SpellDefinition> spells;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadSpells() throws Exception {
        Path yamlPath = Path.of("../assets/data/spells.yaml");
        try (InputStream is = Files.newInputStream(yamlPath)) {
            Map<String, Object> root = new Yaml().load(is);
            Map<String, Map<String, Object>> entries = (Map<String, Map<String, Object>>) root.get("spells");
            spells = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : entries.entrySet()) {
                String name = entry.getKey();
                Map<String, Object> v = entry.getValue();
                String description = v.containsKey("description") ? v.get("description").toString() : "";
                int manaCost = v.containsKey("manaCost") ? ((Number) v.get("manaCost")).intValue() : 0;
                List<EffectNode> effects = AbilityDataParsing.parseEffects(v.get("effects"));
                spells.put(name, new SpellDefinition(name, description, manaCost, null, effects));
            }
        }
    }

    @Test
    void loadsAllSixSpells() {
        assertEquals(6, spells.size());
    }

    @Test
    void allSpellsHaveNonEmptyEffectsAndNonNegativeManaCost() {
        for (SpellDefinition def : spells.values()) {
            assertFalse(def.effects().isEmpty(), def.id() + " must have at least one effect");
            assertTrue(def.manaCost() >= 0, def.id() + " manaCost >= 0");
        }
    }

    @Test
    void fireballKeepsItsOriginalManaCostAndDamageEffect() {
        SpellDefinition fireball = spells.get("Fireball");
        assertNotNull(fireball);
        assertEquals(3, fireball.manaCost());
        assertEquals(1, fireball.effects().size());
        assertEquals("Damage", fireball.effects().get(0).type());
        assertEquals(2, ((Number) fireball.effects().get(0).params().get("amount")).intValue());
    }

    @Test
    void frostboltKeepsItsOriginalManaCostAndTwoEffects() {
        SpellDefinition frostbolt = spells.get("Frostbolt");
        assertNotNull(frostbolt);
        assertEquals(2, frostbolt.manaCost());
        assertEquals(2, frostbolt.effects().size());
    }

    @Test
    void frostboltUsesStunNotAFreezeKeyword() {
        SpellDefinition frostbolt = spells.get("Frostbolt");
        assertNotNull(frostbolt);
        assertFalse(frostbolt.description().toLowerCase().contains("freeze"),
                "afflictions must only ever be described/implemented as Stun, never a separate Freeze keyword");
        assertEquals("ApplyStatus", frostbolt.effects().get(1).type());
        assertEquals("Stun", frostbolt.effects().get(1).params().get("status"));
    }

    @Test
    void healingLightKeepsItsOriginalManaCost() {
        SpellDefinition healingLight = spells.get("Healing Light");
        assertNotNull(healingLight);
        assertEquals(2, healingLight.manaCost());
    }

    @Test
    void chainExamplesArePresent() {
        assertNotNull(spells.get("Scavenge"));
        assertNotNull(spells.get("Chain Lightning"));
        assertNotNull(spells.get("Blizzard"));
    }
}
