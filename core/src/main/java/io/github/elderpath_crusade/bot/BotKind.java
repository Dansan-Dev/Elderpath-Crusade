package io.github.elderpath_crusade.bot;

import io.github.elderpath_crusade.bot.eval.BotConfig;
import io.github.elderpath_crusade.bot.eval.BotPersonality;
import io.github.elderpath_crusade.bot.impl.BasicBot;
import io.github.elderpath_crusade.bot.impl.SmartBot;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.Random;

/**
 * Which bot implementation plays a bot-controlled slot. {@code SMART_AGGRESSIVE}/
 * {@code SMART_DEFENSIVE} (BOT-004) are the smart tier built with a non-default
 * {@link BotPersonality} — same implementation, different {@link BotConfig} weights. Plain
 * {@code SMART} stays the balanced/default personality, so every existing caller is unaffected.
 * These flow through {@code MatchConfig}/{@code LocalMatchSpec}/{@code LaunchOptions} exactly
 * like {@code BASIC}/{@code SMART} already did — a new enum constant, nothing else to widen,
 * since all three already just carry a {@code BotKind} value end to end (including across the
 * CLI boundary via {@code name().toLowerCase()}).
 */
public enum BotKind {
    BASIC,
    SMART,
    SMART_AGGRESSIVE,
    SMART_DEFENSIVE;

    /**
     * Builds the bot for this kind. {@code matchSeed} (the same value {@code MatchConfig.seed}
     * carries — null for an unseeded/random match) plus {@code side} seed the smart tier's own
     * tie-breaking RNG (BOT-002): a fixed match seed must always resolve the same way, same
     * principle as deck shuffling, and the two sides must not share one RNG stream (or they'd
     * always break ties the same way relative to each other, defeating the point for a mirror
     * matchup). {@code side} may be null for a caller with no real side (none today).
     */
    public Bot create(Long matchSeed, PieceAlignment side) {
        Random random = seededRandom(matchSeed, side);
        return switch (this) {
            case BASIC -> new BasicBot();
            case SMART -> new SmartBot(BotConfig.defaultConfig(), random);
            case SMART_AGGRESSIVE -> new SmartBot(BotConfig.forPersonality(BotPersonality.AGGRESSIVE), random);
            case SMART_DEFENSIVE -> new SmartBot(BotConfig.forPersonality(BotPersonality.DEFENSIVE), random);
        };
    }

    private static Random seededRandom(Long matchSeed, PieceAlignment side) {
        if (matchSeed == null) return new Random();
        long sideSalt = side == PieceAlignment.P2 ? 0x2545F4914F6CDD1DL : 0x5DEECE66DL;
        return new Random(matchSeed ^ sideSalt);
    }
}
