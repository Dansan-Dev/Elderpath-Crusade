package io.github.elderpath_crusade.bot.eval;

/**
 * Centralized configuration for bot scoring and heuristic constants.
 */
public record BotConfig(
        int dirForwardBonus,
        int dirBackwardPenalty,

        int scoreWinNow,
        int scoreWinPath1,
        int scoreWinPath2,
        int scoreAdjAttackBase,
        int scoreAdjAttackLethal,
        int scoreRogueLethalMove,
        int scoreAdvanceBase,
        int scoreManeuverBase,
        int scoreSummonBase,

        int penaltyLethalExposure,
        int penaltyThreatBase,
        int penaltyWinPathExposureScale,
        int penaltyWinPathExposureMax,
        int penaltyWinPathEndThreatBase,
        int penaltyWinPathEndThreatExtraScale,
        int penaltyWinPathEndThreatExtraMax,
        int scoreWinPathMin,
        int bonusRogueFreeStrike,
        int bonusRogueLethal,

        int maxWinExpansions) {
    /**
     * Returns a default configuration with standard game values.
     */
    public static BotConfig defaultConfig() {
        return new BotConfig(
                3, // dirForwardBonus
                3, // dirBackwardPenalty
                100, // scoreWinNow
                95, // scoreWinPath1
                88, // scoreWinPath2
                70, // scoreAdjAttackBase
                85, // scoreAdjAttackLethal
                95, // scoreRogueLethalMove
                50, // scoreAdvanceBase
                40, // scoreManeuverBase
                45, // scoreSummonBase
                25, // penaltyLethalExposure
                20, // penaltyThreatBase
                5, // penaltyWinPathExposureScale
                20, // penaltyWinPathExposureMax
                15, // penaltyWinPathEndThreatBase
                5, // penaltyWinPathEndThreatExtraScale
                15, // penaltyWinPathEndThreatExtraMax
                55, // scoreWinPathMin
                28, // bonusRogueFreeStrike
                20, // bonusRogueLethal
                200 // maxWinExpansions
        );
    }

    /**
     * Scales {@link #defaultConfig()} into one of three named playstyles (BOT-004) — a single
     * source of truth for the baseline numbers, rather than three hand-authored copies of ~20
     * weights that would drift out of sync. {@code BALANCED} is the baseline itself, unscaled.
     */
    public static BotConfig forPersonality(BotPersonality personality) {
        BotConfig base = defaultConfig();
        return switch (personality) {
            case BALANCED -> base;
            case AGGRESSIVE -> scaled(base, 1.3f, 0.7f, 0.85f);
            case DEFENSIVE -> scaled(base, 0.85f, 1.3f, 1.2f);
        };
    }

    /**
     * @param offenseScale multiplies attack/advance/win-seeking scores and the forward-move bonus
     * @param cautionScale multiplies risk-aversion penalties and the backward-move penalty
     * @param defenseScale multiplies the base scores for summoning and defensive maneuvering
     */
    private static BotConfig scaled(BotConfig b, float offenseScale, float cautionScale, float defenseScale) {
        return new BotConfig(
                scale(b.dirForwardBonus(), offenseScale),
                scale(b.dirBackwardPenalty(), cautionScale),
                scale(b.scoreWinNow(), offenseScale),
                scale(b.scoreWinPath1(), offenseScale),
                scale(b.scoreWinPath2(), offenseScale),
                scale(b.scoreAdjAttackBase(), offenseScale),
                scale(b.scoreAdjAttackLethal(), offenseScale),
                scale(b.scoreRogueLethalMove(), offenseScale),
                scale(b.scoreAdvanceBase(), offenseScale),
                scale(b.scoreManeuverBase(), defenseScale),
                scale(b.scoreSummonBase(), defenseScale),
                scale(b.penaltyLethalExposure(), cautionScale),
                scale(b.penaltyThreatBase(), cautionScale),
                scale(b.penaltyWinPathExposureScale(), cautionScale),
                scale(b.penaltyWinPathExposureMax(), cautionScale),
                scale(b.penaltyWinPathEndThreatBase(), cautionScale),
                scale(b.penaltyWinPathEndThreatExtraScale(), cautionScale),
                scale(b.penaltyWinPathEndThreatExtraMax(), cautionScale),
                b.scoreWinPathMin(), // the hard floor stays fixed across personalities
                scale(b.bonusRogueFreeStrike(), offenseScale),
                scale(b.bonusRogueLethal(), offenseScale),
                b.maxWinExpansions()); // search budget stays constant across personalities
    }

    private static int scale(int value, float factor) {
        return Math.max(0, Math.round(value * factor));
    }
}
