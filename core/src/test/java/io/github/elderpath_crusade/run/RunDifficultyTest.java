package io.github.elderpath_crusade.run;

import io.github.elderpath_crusade.bot.BotKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunDifficultyTest {

    @Test
    void default_isMinLevel() {
        assertEquals(RunDifficulty.MIN_LEVEL, RunDifficulty.DEFAULT.level());
    }

    @Test
    void accepts_everyLevelInRange() {
        for (int level = RunDifficulty.MIN_LEVEL; level <= RunDifficulty.MAX_LEVEL; level++) {
            assertEquals(level, new RunDifficulty(level).level());
        }
    }

    @Test
    void rejects_belowMin() {
        assertThrows(IllegalArgumentException.class, () -> new RunDifficulty(RunDifficulty.MIN_LEVEL - 1));
    }

    @Test
    void rejects_aboveMax() {
        assertThrows(IllegalArgumentException.class, () -> new RunDifficulty(RunDifficulty.MAX_LEVEL + 1));
    }

    // --- resolveForNode (DIFF-001) ---

    @Test
    void resolveForNode_minLevel_shallowBattleNode_usesBasicBot() {
        var knobs = RunDifficulty.DEFAULT.resolveForNode(0, RunNodeType.BATTLE, 5);
        assertEquals(BotKind.BASIC, knobs.botKind());
        assertEquals(5, knobs.botDeckSize());
    }

    @Test
    void resolveForNode_eliteNode_alwaysSmart_evenAtMinLevelAndZeroDepth() {
        var knobs = RunDifficulty.DEFAULT.resolveForNode(0, RunNodeType.ELITE, 5);
        assertEquals(BotKind.SMART, knobs.botKind());
    }

    @Test
    void resolveForNode_bossNode_alwaysSmart_evenAtMinLevelAndZeroDepth() {
        var knobs = RunDifficulty.DEFAULT.resolveForNode(0, RunNodeType.BOSS, 5);
        assertEquals(BotKind.SMART, knobs.botKind());
    }

    @Test
    void resolveForNode_battleNode_becomesSmartOnceDeepEnoughForTheLevel() {
        RunDifficulty level1 = new RunDifficulty(1);
        assertEquals(BotKind.BASIC, level1.resolveForNode(3, RunNodeType.BATTLE, 5).botKind());
        assertEquals(BotKind.SMART, level1.resolveForNode(4, RunNodeType.BATTLE, 5).botKind());
    }

    @Test
    void resolveForNode_higherLevel_lowersTheDepthNeededForSmartBattleBots() {
        RunDifficulty maxLevel = new RunDifficulty(RunDifficulty.MAX_LEVEL);
        // smart everywhere, even at depth 0, on the hardest level
        assertEquals(BotKind.SMART, maxLevel.resolveForNode(0, RunNodeType.BATTLE, 5).botKind());
    }

    @Test
    void resolveForNode_deckSizeGrowsWithLevelAndDepth() {
        RunDifficulty.ResolvedKnobs base = new RunDifficulty(1).resolveForNode(0, RunNodeType.BATTLE, 5);
        RunDifficulty.ResolvedKnobs deeper = new RunDifficulty(1).resolveForNode(4, RunNodeType.BATTLE, 5);
        RunDifficulty.ResolvedKnobs harder = new RunDifficulty(3).resolveForNode(0, RunNodeType.BATTLE, 5);
        assertEquals(5, base.botDeckSize());
        assertTrue(deeper.botDeckSize() > base.botDeckSize());
        assertTrue(harder.botDeckSize() > base.botDeckSize());
    }

    @Test
    void resolveForNode_rejectsNegativeDepth() {
        assertThrows(IllegalArgumentException.class,
                () -> RunDifficulty.DEFAULT.resolveForNode(-1, RunNodeType.BATTLE, 5));
    }

    @Test
    void resolveForNode_rejectsNonPositiveDeckSize() {
        assertThrows(IllegalArgumentException.class,
                () -> RunDifficulty.DEFAULT.resolveForNode(0, RunNodeType.BATTLE, 0));
    }
}
