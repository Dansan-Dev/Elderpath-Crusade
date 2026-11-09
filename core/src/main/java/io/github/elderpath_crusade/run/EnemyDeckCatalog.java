package io.github.elderpath_crusade.run;

import io.github.elderpath_crusade.bot.BotKind;

import java.util.List;

/**
 * Fixed, hand-picked enemy decks per node type — found via user playtest feedback: the
 * opponent's "deck" used to literally be the player's own deck, cycled to size, so it silently
 * shrank every time a card was promoted to Elder and never felt like a designed opponent.
 * {@link RunManager#buildSpecForCurrentNode} pads whichever of these matches the current node's
 * type out to the resolved bot deck size, exactly the way it used to pad the player's own deck.
 *
 * <p>Reuses {@code CardCatalog}'s existing real roster, picked by name/theme — not verified
 * stats, since {@code pieces.yaml} (the actual numbers) is gitignored and not visible in this
 * sandbox; real balance will need the user's own local playtesting/tuning.
 *
 * <p>{@link #personalityFor} (BOT-004) is the "distinct opponent playstyles" this class's own
 * doc used to flag as future work — Elite and Boss nodes now play more aggressively than an
 * ordinary Battle, whenever the difficulty formula already resolved the smart tier (a Battle
 * node resolved to {@code BASIC} is untouched — a personality only means anything on the smart
 * bot). This only decides *which* personality applies where; {@code BotConfig.forPersonality}
 * is what actually makes one feel different from another.
 */
public final class EnemyDeckCatalog {
    private static final List<String> BATTLE_DECK = List.of("Rogue", "Rogue", "Fairy", "Fairy", "Wind Spirit");
    private static final List<String> ELITE_DECK = List.of("Barbarian", "Barbarian", "Sniper", "Sniper", "Crossbowman");
    private static final List<String> BOSS_DECK = List.of("King", "Commander", "Hero", "Storm Mage", "Barbarian");

    private EnemyDeckCatalog() {}

    /** The enemy deck for this node type — Rest never fights, so it falls back to the Battle deck like anything else unhandled. */
    public static List<String> forNodeType(RunNodeType type) {
        return switch (type) {
            case ELITE -> ELITE_DECK;
            case BOSS -> BOSS_DECK;
            default -> BATTLE_DECK;
        };
    }

    /**
     * Flavors {@code resolvedKind} (whatever {@link RunDifficulty#resolveForNode} already
     * decided) by node type — Elite/Boss upgrade a plain {@code SMART} to
     * {@code SMART_AGGRESSIVE}; every other case (including {@code BASIC}, which has no
     * personality to flavor) passes through unchanged.
     */
    public static BotKind personalityFor(RunNodeType type, BotKind resolvedKind) {
        if (resolvedKind != BotKind.SMART) return resolvedKind;
        return switch (type) {
            case ELITE, BOSS -> BotKind.SMART_AGGRESSIVE;
            default -> resolvedKind;
        };
    }
}
