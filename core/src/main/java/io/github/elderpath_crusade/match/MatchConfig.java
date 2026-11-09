package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.run.RunCardModification;

import java.util.List;
import java.util.Map;

/**
 * Everything needed to start a match's simulation (see MatchSetup) — plain data, so the
 * same config can drive a local room or a headless server.
 *
 * @param decks         card names (CardCatalog display names) per alignment, before shuffling
 * @param seed          RNG seed for deck shuffles/draws; null for a random match
 * @param bots          bot-controlled slots; an alignment missing from the map is played by a human
 * @param relics        active relic ids per alignment (LAUNCH-001) — empty outside a roguelike run.
 *                      A relic's in-battle behavior is curated in {@code abilities.yaml}; this is
 *                      only which ids are active, for whatever spawns the relic carrier (UPG-001).
 * @param cardMods      per-instance card modifications per alignment (LAUNCH-001) — empty outside a
 *                      roguelike run. Registering the synthetic piece/card variants these need is
 *                      UPG-003's job; this only carries the data.
 * @param cardLockTurns how many of that side's own turns must pass before mana gain/card play
 *                      unlock (ELDER-002) — 0 (the default, via {@link #cardLockTurns}) outside a
 *                      roguelike run. Enforced by {@code PlayerManager}/{@code CardRules}.
 */
public record MatchConfig(
        int rows,
        int cols,
        List<Coord> terrain,
        Map<PieceAlignment, List<String>> decks,
        Long seed,
        GameMode mode,
        Map<PieceAlignment, BotKind> bots,
        Map<PieceAlignment, List<String>> relics,
        Map<PieceAlignment, Map<String, RunCardModification>> cardMods,
        Map<PieceAlignment, Integer> cardLockTurns
) {
    public static final int DEFAULT_ROWS = 7;
    public static final int DEFAULT_COLS = 5;
    /** Mountains on the center row, so neither side's home row is closer to them (P1 home row 0, P2 home row ROWS-1). */
    public static final List<Coord> DEFAULT_TERRAIN = List.of(new Coord(3, 1), new Coord(3, 3));

    public MatchConfig {
        terrain = List.copyOf(terrain);
        decks = Map.copyOf(decks);
        bots = Map.copyOf(bots);
        relics = Map.copyOf(relics);
        cardMods = Map.copyOf(cardMods);
        cardLockTurns = Map.copyOf(cardLockTurns);
    }

    /** The standard 7x5 board with its two mountains. No relics/card mods/card-lock — see the {@code with*} methods. */
    public static MatchConfig standard(GameMode mode, List<String> p1Deck, List<String> p2Deck,
                                       Long seed, Map<PieceAlignment, BotKind> bots) {
        return new MatchConfig(DEFAULT_ROWS, DEFAULT_COLS, DEFAULT_TERRAIN,
                Map.of(PieceAlignment.P1, p1Deck, PieceAlignment.P2, p2Deck), seed, mode, bots,
                Map.of(), Map.of(), Map.of());
    }

    public List<String> deck(PieceAlignment alignment) {
        return decks.getOrDefault(alignment, List.of());
    }

    public List<String> relics(PieceAlignment alignment) {
        return relics.getOrDefault(alignment, List.of());
    }

    public Map<String, RunCardModification> cardMods(PieceAlignment alignment) {
        return cardMods.getOrDefault(alignment, Map.of());
    }

    public int cardLockTurns(PieceAlignment alignment) {
        return cardLockTurns.getOrDefault(alignment, 0);
    }

    public MatchConfig withRelics(Map<PieceAlignment, List<String>> value) {
        return new MatchConfig(rows, cols, terrain, decks, seed, mode, bots, value, cardMods, cardLockTurns);
    }

    public MatchConfig withCardMods(Map<PieceAlignment, Map<String, RunCardModification>> value) {
        return new MatchConfig(rows, cols, terrain, decks, seed, mode, bots, relics, value, cardLockTurns);
    }

    public MatchConfig withCardLockTurns(Map<PieceAlignment, Integer> value) {
        return new MatchConfig(rows, cols, terrain, decks, seed, mode, bots, relics, cardMods, value);
    }
}
