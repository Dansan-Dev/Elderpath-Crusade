package io.github.elderpath_crusade.bot.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BOT-005's multi-dimensional personality — each dimension scales (or copies through) a
 * disjoint part of {@link BotConfig}, and every dimension at {@code 0} must contribute nothing,
 * since {@link BotPersonality#BALANCED} (every field {@code 0}) backs every existing caller
 * that doesn't ask for a personality at all.
 */
class BotConfigTest {

    @Test
    void forPersonality_balanced_isTheUnscaledDefault() {
        assertEquals(BotConfig.defaultConfig(), BotConfig.forPersonality(BotPersonality.BALANCED));
    }

    @Test
    void forPersonality_zeroInEveryDimension_isTheUnscaledDefault() {
        BotPersonality allZero = new BotPersonality(0, 0, 0, 0, 0, 0, 0);
        assertEquals(BotConfig.defaultConfig(), BotConfig.forPersonality(allZero));
    }

    @Test
    void forPersonality_aggression_scalesCombatAndCautionOppositely() {
        BotConfig base = BotConfig.defaultConfig();
        BotConfig aggressive = BotConfig.forPersonality(new BotPersonality(1, 0, 0, 0, 0, 0, 0));
        BotConfig cautious = BotConfig.forPersonality(new BotPersonality(-1, 0, 0, 0, 0, 0, 0));

        assertTrue(aggressive.scoreAdjAttackBase() > base.scoreAdjAttackBase());
        assertTrue(aggressive.scoreAdvanceBase() > base.scoreAdvanceBase());
        assertTrue(aggressive.penaltyLethalExposure() < base.penaltyLethalExposure());
        assertTrue(cautious.scoreAdjAttackBase() < base.scoreAdjAttackBase());
        assertTrue(cautious.penaltyLethalExposure() > base.penaltyLethalExposure());
    }

    @Test
    void forPersonality_riskTolerance_onlyScalesTheTradeRiskPenalty_notGeneralCaution() {
        BotConfig base = BotConfig.defaultConfig();
        BotConfig riskTaker = BotConfig.forPersonality(new BotPersonality(0, 1, 0, 0, 0, 0, 0));

        assertTrue(riskTaker.penaltyTradeRiskBase() < base.penaltyTradeRiskBase());
        assertEquals(base.penaltyThreatBase(), riskTaker.penaltyThreatBase(), "general caution is a separate dimension");
        assertEquals(base.penaltyLethalExposure(), riskTaker.penaltyLethalExposure(), "general caution is a separate dimension");
    }

    @Test
    void forPersonality_tempo_onlyScalesWinPathScoring_notCombat() {
        BotConfig base = BotConfig.defaultConfig();
        BotConfig racer = BotConfig.forPersonality(new BotPersonality(0, 0, 1, 0, 0, 0, 0));

        assertTrue(racer.scoreWinNow() > base.scoreWinNow());
        assertTrue(racer.penaltyWinPathExposureMax() < base.penaltyWinPathExposureMax(), "races more willingly into exposure en route to the goal");
        assertEquals(base.scoreAdjAttackBase(), racer.scoreAdjAttackBase(), "combat scoring is a separate dimension");
        assertEquals(base.scoreAdvanceBase(), racer.scoreAdvanceBase(), "combat scoring is a separate dimension");
    }

    @Test
    void forPersonality_saving_isADirectPassThrough_andScalesSummonEagerness() {
        BotConfig base = BotConfig.defaultConfig();
        BotConfig saver = BotConfig.forPersonality(new BotPersonality(0, 0, 0, 1, 0, 0, 0));

        assertEquals(1f, saver.savingBias());
        assertTrue(saver.scoreSummonBase() < base.scoreSummonBase());
    }

    @Test
    void forPersonality_formationAndTargeting_areDirectPassThroughsWithNoOtherEffect() {
        BotConfig base = BotConfig.defaultConfig();
        BotConfig config = BotConfig.forPersonality(new BotPersonality(0, 0, 0, 0, -0.7f, 0.5f, 0));

        assertEquals(-0.7f, config.lanePreference());
        assertEquals(0.5f, config.targetingBias());
        assertEquals(base.scoreAdjAttackBase(), config.scoreAdjAttackBase());
        assertEquals(base.scoreSummonBase(), config.scoreSummonBase());
    }

    @Test
    void forPersonality_unpredictability_widensTheTieBand() {
        assertEquals(5, BotConfig.forPersonality(new BotPersonality(0, 0, 0, 0, 0, 0, 0)).tieBand());
        assertEquals(20, BotConfig.forPersonality(new BotPersonality(0, 0, 0, 0, 0, 0, 1)).tieBand());
    }

    @Test
    void forPersonality_neverScalesTheSearchBudgetOrTheHardFloor() {
        BotConfig base = BotConfig.defaultConfig();
        for (BotPersonality p : new BotPersonality[]{
                BotPersonality.BALANCED, BotPersonality.aggressive(), BotPersonality.defensive(),
                new BotPersonality(1, 1, 1, 1, 1, 1, 1), new BotPersonality(-1, -1, -1, -1, -1, -1, 0)}) {
            BotConfig config = BotConfig.forPersonality(p);
            assertEquals(base.maxWinExpansions(), config.maxWinExpansions(), p + ": search budget must stay constant");
            assertEquals(base.scoreWinPathMin(), config.scoreWinPathMin(), p + ": the hard minimum must stay fixed");
        }
    }

    @Test
    void forPersonality_neverProducesANegativeIntWeight() {
        for (BotPersonality p : new BotPersonality[]{
                BotPersonality.aggressive(), BotPersonality.defensive(),
                new BotPersonality(1, 1, 1, 1, 1, 1, 1), new BotPersonality(-1, -1, -1, -1, -1, -1, 0)}) {
            BotConfig config = BotConfig.forPersonality(p);
            assertTrue(config.scoreAdjAttackBase() >= 0);
            assertTrue(config.penaltyThreatBase() >= 0);
            assertTrue(config.penaltyTradeRiskBase() >= 0);
            assertTrue(config.dirBackwardPenalty() >= 0);
            assertTrue(config.tieBand() >= 5);
        }
    }

    @Test
    void personality_clampsEachSignedDimensionToUnitRange() {
        BotPersonality extreme = new BotPersonality(5, -5, 5, -5, 5, -5, 5);
        assertEquals(1f, extreme.aggression());
        assertEquals(-1f, extreme.riskTolerance());
        assertEquals(1f, extreme.tempo());
        assertEquals(-1f, extreme.saving());
        assertEquals(1f, extreme.formation());
        assertEquals(-1f, extreme.targeting());
        assertEquals(1f, extreme.unpredictability(), "unpredictability clamps to [0,1], not [-1,1]");
    }
}
