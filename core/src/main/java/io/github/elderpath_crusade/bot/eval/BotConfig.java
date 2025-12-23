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
}
