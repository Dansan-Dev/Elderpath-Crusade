package io.github.elderpath_crusade.data;

import io.github.elderpath_crusade.abilities.data.Reaction;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage: CleaveAttack (Barbarian) was centered on the target's tile — it
 * should splash adjacent enemies of the attacker itself, and must exclude the primary
 * target so it isn't hit twice (it's always adjacent to a melee attacker).
 */
@RequiresAssets
class CleaveAttackYamlTest {

    @Test
    @SuppressWarnings("unchecked")
    void cleaveAttack_centersOnSelf_andExcludesThePrimaryTarget() throws Exception {
        Map<String, Object> abilities;
        try (InputStream is = Files.newInputStream(Path.of("../assets/data/abilities.yaml"))) {
            Map<String, Object> root = new Yaml().load(is);
            abilities = (Map<String, Object>) root.get("abilities");
        }

        Map<String, Object> def = (Map<String, Object>) abilities.get("CleaveAttack");
        List<Reaction> reactions = AbilityDataParsing.parseReactions(def.get("reactions"));
        Map<String, Object> foreachParams = reactions.get(0).effects().get(0).params();
        Map<String, Object> targets = (Map<String, Object>) foreachParams.get("targets");
        Map<String, Object> selectorParams = (Map<String, Object>) targets.get("params");

        assertEquals("$self.row", selectorParams.get("row"));
        assertEquals("$self.col", selectorParams.get("col"));
        assertEquals("$event.defenderEntity", selectorParams.get("exclude"));
    }
}
