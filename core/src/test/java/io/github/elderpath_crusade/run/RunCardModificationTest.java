package io.github.elderpath_crusade.run;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The text format used to pass modifications across the client→battle-launch boundary (LAUNCH-001). */
class RunCardModificationTest {

    @Test
    void toSpec_and_parseSpec_roundTrip_statsAndAbilities() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("damage", 2);
        stats.put("health", -1);
        RunCardModification mod = new RunCardModification(stats, List.of("Frenzy", "Tough"));

        RunCardModification roundTripped = RunCardModification.parseSpec(mod.toSpec());

        assertEquals(mod, roundTripped);
    }

    @Test
    void toSpec_and_parseSpec_roundTrip_statsOnly() {
        RunCardModification mod = new RunCardModification(Map.of("damage", 1), List.of());
        assertEquals(mod, RunCardModification.parseSpec(mod.toSpec()));
    }

    @Test
    void toSpec_and_parseSpec_roundTrip_abilitiesOnly() {
        RunCardModification mod = new RunCardModification(Map.of(), List.of("Berserk"));
        assertEquals(mod, RunCardModification.parseSpec(mod.toSpec()));
    }

    @Test
    void toSpec_and_parseSpec_roundTrip_empty() {
        assertEquals(RunCardModification.NONE, RunCardModification.parseSpec(RunCardModification.NONE.toSpec()));
    }

    @Test
    void parseSpec_rejectsMissingColon() {
        assertThrows(IllegalArgumentException.class, () -> RunCardModification.parseSpec("damage=2"));
    }

    @Test
    void parseSpec_rejectsMissingEquals() {
        assertThrows(IllegalArgumentException.class, () -> RunCardModification.parseSpec("damage2:"));
    }

    @Test
    void parseSpec_rejectsNonNumericDelta() {
        assertThrows(IllegalArgumentException.class, () -> RunCardModification.parseSpec("damage=two:"));
    }

    @Test
    void encodeMap_and_decodeMap_roundTrip_multipleInstances() {
        Map<String, RunCardModification> mods = new LinkedHashMap<>();
        mods.put("run-3", new RunCardModification(Map.of("damage", 2), List.of("Frenzy")));
        mods.put("run-5", new RunCardModification(Map.of(), List.of("Berserk", "Tough")));

        String encoded = RunCardModification.encodeMap(mods);
        Map<String, RunCardModification> decoded = RunCardModification.decodeMap(encoded);

        assertEquals(mods, decoded);
    }

    @Test
    void encodeMap_ofEmptyMap_decodesBackToEmpty() {
        assertEquals(Map.of(), RunCardModification.decodeMap(RunCardModification.encodeMap(Map.of())));
    }

    @Test
    void decodeMap_ofBlankOrNull_isEmpty() {
        assertTrue(RunCardModification.decodeMap("").isEmpty());
        assertTrue(RunCardModification.decodeMap(null).isEmpty());
    }

    @Test
    void decodeMap_rejectsMissingAt() {
        assertThrows(IllegalArgumentException.class, () -> RunCardModification.decodeMap("run-3damage=2:"));
    }

    @Test
    void plus_sumsOverlappingStatDeltasAndConcatenatesAbilities() {
        RunCardModification base = new RunCardModification(Map.of("damage", 1, "health", -1), List.of("Frenzy"));
        RunCardModification extra = new RunCardModification(Map.of("damage", 2, "speed", 1), List.of("Tough"));

        RunCardModification combined = base.plus(extra);

        assertEquals(Map.of("damage", 3, "health", -1, "speed", 1), combined.statDeltas());
        assertEquals(List.of("Frenzy", "Tough"), combined.addedAbilities());
    }

    @Test
    void plus_withNone_isUnchanged() {
        RunCardModification mod = new RunCardModification(Map.of("damage", 1), List.of("Frenzy"));
        assertEquals(mod, mod.plus(RunCardModification.NONE));
    }

    @Test
    void encodeMap_rejectsInstanceIdContainingDelimiters() {
        assertThrows(IllegalArgumentException.class,
                () -> RunCardModification.encodeMap(Map.of("run@3", RunCardModification.NONE)));
        assertThrows(IllegalArgumentException.class,
                () -> RunCardModification.encodeMap(Map.of("run;3", RunCardModification.NONE)));
    }
}
