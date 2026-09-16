package io.github.elderpath_crusade.data;

import io.github.elderpath_crusade.abilities.data.ActionDef;
import io.github.elderpath_crusade.abilities.data.Cost;
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
 * Regression coverage: StormAction only charged Mana, so casting it never spent an
 * action point — a piece could use it and still attack/move/act again the same turn.
 */
@RequiresAssets
class StormActionCostTest {

    @Test
    @SuppressWarnings("unchecked")
    void stormAction_costsBothAnActionAndMana() throws Exception {
        Map<String, Object> abilities;
        try (InputStream is = Files.newInputStream(Path.of("../assets/data/abilities.yaml"))) {
            Map<String, Object> root = new Yaml().load(is);
            abilities = (Map<String, Object>) root.get("abilities");
        }

        Map<String, Object> def = (Map<String, Object>) abilities.get("StormAction");
        assertNotNull(def);
        List<ActionDef> actions = AbilityDataParsing.parseActions(def.get("actions"));
        assertEquals(1, actions.size());
        List<Cost> costs = actions.get(0).costs();

        assertTrue(costs.stream().anyMatch(c -> "Action".equals(c.type()) && c.amount() == 1),
                "StormAction must cost 1 action");
        assertTrue(costs.stream().anyMatch(c -> "Mana".equals(c.type()) && c.amount() == 1),
                "StormAction must cost 1 mana");
    }
}
