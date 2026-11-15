package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.data.AbilityRef;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.path_loaders.ImagePathIcons;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** UI-003: which abilities become keyword badges and which stay rules text. Pure logic, no GL. */
class CardKeywordsTest {

    // The registry is static and shared across test classes: put back whatever was there.
    private AbilityDefinition previousRange;
    private AbilityDefinition previousCannotAttack;

    @AfterEach
    void restoreAbilities() {
        if (previousRange != null) AbilityRegistry.register(previousRange);
        if (previousCannotAttack != null) AbilityRegistry.register(previousCannotAttack);
    }

    @BeforeEach
    void registerAbilities() {
        previousRange = AbilityRegistry.get("RangeBonus");
        previousCannotAttack = AbilityRegistry.get("CannotAttack");
        AbilityRegistry.register(new AbilityDefinition("RangeBonus", "Range {addRange}.", null, List.of(), List.of(), List.of()));
        AbilityRegistry.register(new AbilityDefinition("CannotAttack", "Cannot attack.", null, List.of(), List.of(), List.of()));
        AbilityRegistry.register(new AbilityDefinition("TestCleave", "Hits adjacent foes too.", null, List.of(), List.of(), List.of()));
    }

    @Test
    void keywordsBecomeBadges_otherAbilitiesStayProse_inOrder() {
        CardKeywords.Split split = CardKeywords.split(List.of(
                new AbilityRef("TestCleave"),
                new AbilityRef("RangeBonus", Map.of("addRange", 3)),
                new AbilityRef("CannotAttack")));

        assertEquals(List.of(
                new CardKeywords.Keyword(ImagePathIcons.KEYWORD_RANGE, "3"),
                new CardKeywords.Keyword(ImagePathIcons.KEYWORD_CANNOT_ATTACK, "")), split.keywords());
        assertEquals(List.of("Hits adjacent foes too."), split.descriptions());
    }

    @Test
    void keywordWithoutItsIcon_fallsBackToSpecializedProse() {
        CardKeywords.Split split = CardKeywords.split(
                List.of(new AbilityRef("RangeBonus", Map.of("addRange", 4))), icon -> false);

        assertTrue(split.keywords().isEmpty());
        assertEquals(List.of("Range 4."), split.descriptions());
    }

    @Test
    void noAbilities_orUnknownAbility_isEmptyNotAnError() {
        assertTrue(CardKeywords.split(null).keywords().isEmpty());
        CardKeywords.Split split = CardKeywords.split(List.of(new AbilityRef("NoSuchAbility")));
        assertTrue(split.keywords().isEmpty());
        assertTrue(split.descriptions().isEmpty());
    }
}
