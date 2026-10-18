package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.List;
import java.util.Map;

/**
 * Everything needed to start a match's simulation (see MatchSetup) — plain data, so the
 * same config can drive a local room or a headless server.
 *
 * @param decks card names (CardCatalog display names) per alignment, before shuffling
 * @param seed  RNG seed for deck shuffles/draws; null for a random match
 * @param bots  bot-controlled slots; an alignment missing from the map is played by a human
 */
public record MatchConfig(
        int rows,
        int cols,
        List<Coord> terrain,
        Map<PieceAlignment, List<String>> decks,
        Long seed,
        GameMode mode,
        Map<PieceAlignment, BotKind> bots
) {
    public static final int DEFAULT_ROWS = 7;
    public static final int DEFAULT_COLS = 5;
    /** Mountains on the center row, so neither side's home row is closer to them (P1 home row 0, P2 home row ROWS-1). */
    public static final List<Coord> DEFAULT_TERRAIN = List.of(new Coord(3, 1), new Coord(3, 3));

    public MatchConfig {
        terrain = List.copyOf(terrain);
        decks = Map.copyOf(decks);
        bots = Map.copyOf(bots);
    }

    /** The standard 7x5 board with its two mountains. */
    public static MatchConfig standard(GameMode mode, List<String> p1Deck, List<String> p2Deck,
                                       Long seed, Map<PieceAlignment, BotKind> bots) {
        return new MatchConfig(DEFAULT_ROWS, DEFAULT_COLS, DEFAULT_TERRAIN,
                Map.of(PieceAlignment.P1, p1Deck, PieceAlignment.P2, p2Deck), seed, mode, bots);
    }

    public List<String> deck(PieceAlignment alignment) {
        return decks.getOrDefault(alignment, List.of());
    }
}
