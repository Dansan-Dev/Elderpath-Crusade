package io.github.elderpath_crusade.run;

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
}
