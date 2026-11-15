package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.data.AbilityRef;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.path_loaders.ImagePathIcons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UI-003: splits a piece's abilities into keyword badges (an icon, plus the ability's one
 * parameter as a short label, e.g. Range's {@code addRange}) and the rules text every other
 * ability still shows as prose. Keywords are the abilities repeated across several pieces —
 * currently {@code RangeBonus} and {@code CannotAttack}. A keyword stays prose if its icon is
 * missing, decided by the caller ({@link #split(List, java.util.function.Predicate)}).
 *
 * <p>Pure logic (no textures, no GL) so it is headless-testable; shared by {@link UnitCard}'s
 * own construction and {@code CardPreviewPanel}'s hover preview, which builds its text itself.
 */
public final class CardKeywords {
    /** Ability id → its badge icon. */
    static final Map<String, ImagePathIcons> KEYWORD_ICONS = Map.of(
            "RangeBonus", ImagePathIcons.KEYWORD_RANGE,
            "CannotAttack", ImagePathIcons.KEYWORD_CANNOT_ATTACK);

    /** One badge: its icon and a short label (empty when the ability has no parameter). */
    public record Keyword(ImagePathIcons icon, String label) {
    }

    /** The badges, then the prose descriptions of every non-keyword ability, in ability order. */
    public record Split(List<Keyword> keywords, List<String> descriptions) {
    }

    private CardKeywords() {
    }

    /** Every keyword becomes a badge. */
    public static Split split(List<AbilityRef> refs) {
        return split(refs, icon -> true);
    }

    /** As {@link #split(List)}, but a keyword whose icon fails {@code iconAvailable} stays prose. */
    public static Split split(List<AbilityRef> refs, java.util.function.Predicate<ImagePathIcons> iconAvailable) {
        List<Keyword> keywords = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        if (refs == null) return new Split(keywords, descriptions);
        for (AbilityRef ref : refs) {
            ImagePathIcons icon = KEYWORD_ICONS.get(ref.name());
            if (icon != null && iconAvailable.test(icon)) {
                keywords.add(new Keyword(icon, labelFor(ref)));
                continue;
            }
            AbilityDefinition def = ref.params().isEmpty()
                    ? AbilityRegistry.get(ref.name())
                    : AbilityRegistry.specialize(ref.name(), ref.params());
            if (def != null && def.description() != null && !def.description().isEmpty()) {
                descriptions.add(def.description());
            }
        }
        return new Split(keywords, descriptions);
    }

    /** A keyword's one parameter value (e.g. "3" for {@code addRange: 3}), or "" if it has none. */
    static String labelFor(AbilityRef ref) {
        if (ref.params() == null || ref.params().isEmpty()) return "";
        Object value = ref.params().values().iterator().next();
        return value == null ? "" : String.valueOf(value);
    }
}
