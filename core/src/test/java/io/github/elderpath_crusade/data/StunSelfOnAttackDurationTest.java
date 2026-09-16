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
 * Regression coverage: StunSelfOnAttack applied 3 turns of Stun while its own
 * description said "stunned for 2 turns" — a Sniper that attacked would still show 1
 * turn of stun left after its description's promised duration had supposedly expired
 * (and could feed a stale, longer-than-expected duration into a later stacking Stun,
 * e.g. Blizzard's, via ApplyStatus's max-of-existing-and-new behavior).
 */
@RequiresAssets
class StunSelfOnAttackDurationTest {

    @Test
    @SuppressWarnings("unchecked")
    void turnsMatchesItsOwnDescription() throws Exception {
        Map<String, Object> abilities;
        try (InputStream is = Files.newInputStream(Path.of("../assets/data/abilities.yaml"))) {
            Map<String, Object> root = new Yaml().load(is);
            abilities = (Map<String, Object>) root.get("abilities");
        }

        Map<String, Object> def = (Map<String, Object>) abilities.get("StunSelfOnAttack");
        assertNotNull(def);
        String description = (String) def.get("description");
        assertTrue(description.contains("2 turns"), "sanity check: description still says 2 turns");

        List<Reaction> reactions = AbilityDataParsing.parseReactions(def.get("reactions"));
        int turns = ((Number) reactions.get(0).effects().get(0).params().get("turns")).intValue();
        assertEquals(2, turns, "the applied Stun duration must match the '2 turns' the description promises");
    }
}
