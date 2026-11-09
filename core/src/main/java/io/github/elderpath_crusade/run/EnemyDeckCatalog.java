package io.github.elderpath_crusade.run;

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
 * sandbox; real balance will need the user's own local playtesting/tuning. "Personalities"
 * (distinct opponent playstyles) are explicitly future work per the user, not attempted here —
 * this only varies the *cards*, by node type, not how the bot plays them.
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
}
