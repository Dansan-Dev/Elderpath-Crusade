package io.github.elderpath_crusade.bot.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** BOT-004's personality scaling — a single source of truth (defaultConfig) scaled per playstyle. */
class BotConfigTest {

    @Test
    void forPersonality_balanced_isTheUnscaledDefault() {
        assertEquals(BotConfig.defaultConfig(), BotConfig.forPersonality(BotPersonality.BALANCED));
    }

    @Test
    void forPersonality_aggressive_raisesOffenseAndLowersCaution() {
        BotConfig base = BotConfig.defaultConfig();
        BotConfig aggressive = BotConfig.forPersonality(BotPersonality.AGGRESSIVE);

        assertTrue(aggressive.scoreAdjAttackBase() > base.scoreAdjAttackBase(), "attacks should score higher");
        assertTrue(aggressive.scoreAdvanceBase() > base.scoreAdvanceBase(), "advancing should score higher");
        assertTrue(aggressive.scoreWinNow() > base.scoreWinNow(), "win-seeking should score higher");
        assertTrue(aggressive.penaltyThreatBase() < base.penaltyThreatBase(), "risk should matter less");
        assertTrue(aggressive.penaltyLethalExposure() < base.penaltyLethalExposure(), "exposure should matter less");
    }

    @Test
    void forPersonality_defensive_raisesCautionAndLowersOffense() {
        BotConfig base = BotConfig.defaultConfig();
        BotConfig defensive = BotConfig.forPersonality(BotPersonality.DEFENSIVE);

        assertTrue(defensive.scoreAdjAttackBase() < base.scoreAdjAttackBase(), "attacks should score lower");
        assertTrue(defensive.scoreAdvanceBase() < base.scoreAdvanceBase(), "advancing should score lower");
        assertTrue(defensive.penaltyThreatBase() > base.penaltyThreatBase(), "risk should matter more");
        assertTrue(defensive.scoreSummonBase() > base.scoreSummonBase(), "board development should score higher");
        assertTrue(defensive.scoreManeuverBase() > base.scoreManeuverBase(), "playing safe should score higher");
    }

    @Test
    void forPersonality_neverScalesTheSearchBudgetOrTheHardFloor() {
        BotConfig base = BotConfig.defaultConfig();
        for (BotPersonality personality : BotPersonality.values()) {
            BotConfig config = BotConfig.forPersonality(personality);
            assertEquals(base.maxWinExpansions(), config.maxWinExpansions(),
                    personality + ": search budget must stay constant across personalities");
            assertEquals(base.scoreWinPathMin(), config.scoreWinPathMin(),
                    personality + ": the hard minimum must stay fixed across personalities");
        }
    }

    @Test
    void forPersonality_neverProducesANegativeWeight() {
        for (BotPersonality personality : BotPersonality.values()) {
            BotConfig config = BotConfig.forPersonality(personality);
            assertTrue(config.scoreAdjAttackBase() >= 0);
            assertTrue(config.penaltyThreatBase() >= 0);
            assertTrue(config.dirBackwardPenalty() >= 0);
        }
    }
}
