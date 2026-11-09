package io.github.elderpath_crusade.bot.eval;

/**
 * A named playstyle a {@code SmartBot} can be built with (BOT-004) — the "future work" the user
 * flagged while reviewing {@code EnemyDeckCatalog}: enemies should eventually play differently
 * from each other, not just field different cards. Each variant maps to a {@link BotConfig} via
 * {@link BotConfig#forPersonality}, which scales the same default weights rather than hand-
 * authoring three separate sets of ~20 magic numbers.
 */
public enum BotPersonality {
    /** The existing default — unscaled, zero behavior change for any caller that doesn't ask for a personality. */
    BALANCED,
    /** Pushes harder for the win and trades more readily; cares less about exposing itself to a counter-attack. */
    AGGRESSIVE,
    /** Builds its board and plays it safe; values avoiding damage and holding position over racing for the win. */
    DEFENSIVE
}
