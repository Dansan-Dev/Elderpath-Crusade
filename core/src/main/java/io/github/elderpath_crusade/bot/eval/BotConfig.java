package io.github.elderpath_crusade.bot.eval;

/**
 * Centralized configuration for bot scoring and heuristic constants. {@link #forPersonality}
 * (BOT-005) is the only thing that varies these — every evaluator just reads a {@code BotConfig}
 * it's handed, with no notion of personality of its own.
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

        int maxWinExpansions,

        /** BOT-005 (riskTolerance): the trade-specific exposure penalty in {@code AttackEvaluator} —
         *  split out from {@code penaltyThreatBase} (which stays {@code aggression}-scaled and is used
         *  everywhere else) so risk tolerance only touches combat trades, not general caution. */
        int penaltyTradeRiskBase,
        /** BOT-005 (saving), -1..+1: direct pass-through, read by {@code SummonEvaluator}. */
        float savingBias,
        /** BOT-005 (formation), -1..+1: direct pass-through, read by {@code SummonEvaluator}/{@code MovementEvaluator}. */
        float lanePreference,
        /** BOT-005 (targeting), -1..+1: direct pass-through, read by {@code AttackEvaluator}. */
        float targetingBias,
        /** BOT-002/BOT-005 (unpredictability): how wide a net {@code SmartBot} casts among tied-best intents. */
        int tieBand) {
    /**
     * Returns a default configuration with standard game values — exactly what {@code
     * forPersonality(BotPersonality.BALANCED)} also produces, field for field.
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
                200, // maxWinExpansions
                20, // penaltyTradeRiskBase — same default as penaltyThreatBase, since it replaces that field's one use in AttackEvaluator
                0f, // savingBias
                0f, // lanePreference
                0f, // targetingBias
                5 // tieBand — matches BOT-002's original fixed TIE_BAND
        );
    }

    /**
     * Scales {@link #defaultConfig()} per {@link BotPersonality} (BOT-005) — a single source of
     * truth for the baseline numbers. Each dimension scales (or copies through) a disjoint set
     * of fields, so they compose independently; every scale factor is {@code 1.0} (or the
     * dimension's raw value is {@code 0}) at {@link BotPersonality#BALANCED}, reproducing
     * {@link #defaultConfig()} exactly.
     */
    public static BotConfig forPersonality(BotPersonality p) {
        BotConfig b = defaultConfig();

        float offenseScale = 1 + p.aggression() * 0.3f;
        float cautionScale = 1 - p.aggression() * 0.3f;
        float defenseScale = 1 - p.aggression() * 0.15f;
        float tempoScale = 1 + p.tempo() * 0.35f;
        float winPathSafetyScale = 1 - p.tempo() * 0.35f;
        float tradeRiskScale = 1 - p.riskTolerance() * 0.45f;
        float summonEagernessScale = 1 - p.saving() * 0.3f;

        return new BotConfig(
                scale(b.dirForwardBonus(), offenseScale),
                scale(b.dirBackwardPenalty(), cautionScale),
                scale(b.scoreWinNow(), tempoScale),
                scale(b.scoreWinPath1(), tempoScale),
                scale(b.scoreWinPath2(), tempoScale),
                scale(b.scoreAdjAttackBase(), offenseScale),
                scale(b.scoreAdjAttackLethal(), offenseScale),
                scale(b.scoreRogueLethalMove(), offenseScale),
                scale(b.scoreAdvanceBase(), offenseScale),
                scale(b.scoreManeuverBase(), defenseScale),
                scale(b.scoreSummonBase(), summonEagernessScale),
                scale(b.penaltyLethalExposure(), cautionScale),
                scale(b.penaltyThreatBase(), cautionScale),
                scale(b.penaltyWinPathExposureScale(), winPathSafetyScale),
                scale(b.penaltyWinPathExposureMax(), winPathSafetyScale),
                scale(b.penaltyWinPathEndThreatBase(), winPathSafetyScale),
                scale(b.penaltyWinPathEndThreatExtraScale(), winPathSafetyScale),
                scale(b.penaltyWinPathEndThreatExtraMax(), winPathSafetyScale),
                b.scoreWinPathMin(), // the hard floor stays fixed across personalities
                scale(b.bonusRogueFreeStrike(), offenseScale),
                scale(b.bonusRogueLethal(), offenseScale),
                b.maxWinExpansions(), // search budget stays constant across personalities
                scale(b.penaltyTradeRiskBase(), tradeRiskScale),
                p.saving(),
                p.formation(),
                p.targeting(),
                Math.round(5 + p.unpredictability() * 15));
    }

    private static int scale(int value, float factor) {
        return Math.max(0, Math.round(value * factor));
    }
}
