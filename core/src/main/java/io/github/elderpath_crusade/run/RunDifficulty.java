package io.github.elderpath_crusade.run;

import io.github.elderpath_crusade.bot.BotKind;

/**
 * The run's chosen difficulty level, fixed for the whole run (player-selected at run start, via
 * the difficulty-select room) — combined with each node's depth (distance from the start,
 * {@link RunMap#depths()}) by {@link #resolveForNode}, which decides what knobs a node's battle
 * should actually run with ({@code BotKind}, bot deck size). Pure formula, no new battle-side
 * mechanics — it drives the existing {@code LocalMatchSpec}/{@code BotKind} knobs, same seed +
 * level + depth always resolves the same way.
 */
public record RunDifficulty(int level) {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;
    public static final RunDifficulty DEFAULT = new RunDifficulty(MIN_LEVEL);

    public RunDifficulty {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("level must be between " + MIN_LEVEL + " and " + MAX_LEVEL);
        }
    }

    /** What a node's bot opponent should play like, and how large its deck should be. */
    public record ResolvedKnobs(BotKind botKind, int botDeckSize) {
    }

    /**
     * Resolves this level's knobs for a node at the given depth. Elite and Boss nodes always
     * get the smarter bot regardless of level/depth; a Battle node gets it once the run is deep
     * enough for the chosen level — the depth threshold drops as the level rises, down to
     * "smart everywhere from the very first node" at the max level. The bot's deck grows by one
     * card for every level above the minimum, plus one for every two floors of depth.
     */
    public ResolvedKnobs resolveForNode(int depth, RunNodeType nodeType, int playerDeckSize) {
        if (depth < 0) throw new IllegalArgumentException("depth must be >= 0");
        if (playerDeckSize < 1) throw new IllegalArgumentException("playerDeckSize must be >= 1");
        boolean smart = nodeType == RunNodeType.ELITE || nodeType == RunNodeType.BOSS
                || depth >= smartBotDepthThreshold();
        BotKind botKind = smart ? BotKind.SMART : BotKind.BASIC;
        int extraCards = (level - MIN_LEVEL) + depth / 2;
        return new ResolvedKnobs(botKind, playerDeckSize + extraCards);
    }

    /** The depth at which every Battle node's bot becomes SMART, decreasing as the level rises. */
    private int smartBotDepthThreshold() {
        return switch (level) {
            case 1 -> 4;
            case 2 -> 3;
            case 3 -> 2;
            case 4 -> 1;
            default -> 0;
        };
    }
}
