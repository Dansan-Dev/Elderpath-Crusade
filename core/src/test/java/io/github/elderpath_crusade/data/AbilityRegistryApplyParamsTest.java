package io.github.elderpath_crusade.data;

import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ModifierDef;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage: Sniper/Rifleman/Crossbowman's range bonuses should reuse a single
 * "RangeBonus" ability template parameterized per piece, rather than three near-identical
 * abilities (SniperRange/RiflemanRange/CrossbowmanRange) that only differ by a number.
 */
class AbilityRegistryApplyParamsTest {

    private AbilityDefinition rangeBonusTemplate() {
        return new AbilityDefinition(
                "RangeBonus", "Range {addRange}.", Map.of(), null, null,
                List.of(new ModifierDef(new TargetSelector("Self"), Map.of())));
    }

    @Test
    void applyParams_overridesModifierStat() {
        AbilityDefinition specialized = AbilityRegistry.applyParams(rangeBonusTemplate(), Map.of("addRange", 4));

        assertEquals(1, specialized.modifiers().size());
        assertEquals(4, specialized.modifiers().get(0).stats().get("addRange"));
    }

    @Test
    void applyParams_substitutesDescriptionPlaceholder() {
        AbilityDefinition specialized = AbilityRegistry.applyParams(rangeBonusTemplate(), Map.of("addRange", 2));
        assertEquals("Range 2.", specialized.description());
    }

    @Test
    void applyParams_differentParams_produceIndependentDefinitions() {
        AbilityDefinition template = rangeBonusTemplate();
        AbilityDefinition sniper = AbilityRegistry.applyParams(template, Map.of("addRange", 4));
        AbilityDefinition rifleman = AbilityRegistry.applyParams(template, Map.of("addRange", 2));

        assertEquals(4, sniper.modifiers().get(0).stats().get("addRange"));
        assertEquals(2, rifleman.modifiers().get(0).stats().get("addRange"));
        // The template itself must remain untouched (no shared mutable state between pieces).
        assertTrue(template.modifiers().get(0).stats().isEmpty());
    }

    @Test
    void applyParams_noParams_returnsBaseUnchanged() {
        AbilityDefinition template = rangeBonusTemplate();
        assertSame(template, AbilityRegistry.applyParams(template, Map.of()));
    }
}
