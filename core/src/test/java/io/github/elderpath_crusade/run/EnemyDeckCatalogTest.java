package io.github.elderpath_crusade.run;

import io.github.elderpath_crusade.bot.BotKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnemyDeckCatalogTest {

    @Test
    void forNodeType_isNonEmptyForEveryNodeType() {
        for (RunNodeType type : RunNodeType.values()) {
            assertFalse(EnemyDeckCatalog.forNodeType(type).isEmpty(), type + " has no enemy deck");
        }
    }

    @Test
    void forNodeType_eliteAndBossDecksDifferFromBattle() {
        assertNotEquals(EnemyDeckCatalog.forNodeType(RunNodeType.BATTLE), EnemyDeckCatalog.forNodeType(RunNodeType.ELITE));
        assertNotEquals(EnemyDeckCatalog.forNodeType(RunNodeType.BATTLE), EnemyDeckCatalog.forNodeType(RunNodeType.BOSS));
        assertNotEquals(EnemyDeckCatalog.forNodeType(RunNodeType.ELITE), EnemyDeckCatalog.forNodeType(RunNodeType.BOSS));
    }

    @Test
    void forNodeType_isStableAcrossCalls() {
        assertEquals(EnemyDeckCatalog.forNodeType(RunNodeType.BATTLE), EnemyDeckCatalog.forNodeType(RunNodeType.BATTLE));
    }

    // --- BOT-004: node-type personality flavoring ---

    @Test
    void personalityFor_battleAndRecruitment_leaveSmartUnflavored() {
        assertEquals(BotKind.SMART, EnemyDeckCatalog.personalityFor(RunNodeType.BATTLE, BotKind.SMART));
        assertEquals(BotKind.SMART, EnemyDeckCatalog.personalityFor(RunNodeType.RECRUITMENT, BotKind.SMART));
    }

    @Test
    void personalityFor_eliteAndBoss_upgradeSmartToAggressive() {
        assertEquals(BotKind.SMART_AGGRESSIVE, EnemyDeckCatalog.personalityFor(RunNodeType.ELITE, BotKind.SMART));
        assertEquals(BotKind.SMART_AGGRESSIVE, EnemyDeckCatalog.personalityFor(RunNodeType.BOSS, BotKind.SMART));
    }

    @Test
    void personalityFor_basic_isNeverFlavored() {
        for (RunNodeType type : RunNodeType.values()) {
            assertEquals(BotKind.BASIC, EnemyDeckCatalog.personalityFor(type, BotKind.BASIC),
                    type + ": BASIC has no personality to flavor");
        }
    }
}
