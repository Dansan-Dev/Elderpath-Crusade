package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.stats.StatsAccumulator;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EcsStatsSyncTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
    }

    @Test
    void gamePieceStats_beforeLink_readsLocalValues() {
        GamePieceStats stats = GamePieceStats.getMonsterStats(2, 5, 3, 1, 2);

        assertEquals(2, stats.getCost());
        assertEquals(5, stats.getMaxHealth());
        assertEquals(3, stats.getDamage());
        assertEquals(1, stats.getSpeed());
        assertEquals(2, stats.getActions());
        assertEquals(5, stats.getCurrentHealth());
    }

    @Test
    void gamePieceStats_afterLink_readsFromStatsComponent() {
        GamePieceStats stats = GamePieceStats.getMonsterStats(2, 5, 3, 1, 2);
        Entity entity = engine.createEntity();
        entity.add(new StatsComponent().set(2, 5, 3, 1, 2));
        engine.addEntity(entity);

        stats.linkEntity(entity);
        entity.getComponent(StatsComponent.class).damage = 10;

        assertEquals(10, stats.getDamage());
    }

    @Test
    void gamePieceStats_afterLink_writesGoToStatsComponent() {
        GamePieceStats stats = GamePieceStats.getMonsterStats(2, 5, 3, 1, 2);
        Entity entity = engine.createEntity();
        entity.add(new StatsComponent().set(2, 5, 3, 1, 2));
        engine.addEntity(entity);
        stats.linkEntity(entity);

        stats.dealDamage(2);

        assertEquals(3, entity.getComponent(StatsComponent.class).currentHealth);
        assertEquals(3, stats.getCurrentHealth());
    }

    @Test
    void gamePieceStats_afterLink_setRemainingActions() {
        GamePieceStats stats = GamePieceStats.getMonsterStats(2, 5, 3, 1, 2);
        Entity entity = engine.createEntity();
        entity.add(new StatsComponent().set(2, 5, 3, 1, 2));
        engine.addEntity(entity);
        stats.linkEntity(entity);

        stats.setRemainingActions(1);

        assertEquals(1, entity.getComponent(StatsComponent.class).remainingActions);
    }

    @Test
    void monsterGamePiece_setEntity_linksStats() {
        GamePieceStats stats = GamePieceStats.getMonsterStats(2, 5, 3, 1, 2);
        MonsterGamePiece piece = new MonsterGamePiece(stats, GamePieceType.MONSTER, PieceAlignment.P1, UUID.randomUUID(), null);
        Entity entity = engine.createEntity();
        entity.add(new StatsComponent().set(2, 5, 3, 1, 2));
        engine.addEntity(entity);

        piece.setEntity(entity);
        entity.getComponent(StatsComponent.class).damage = 7;

        assertEquals(7, piece.getStats().getDamage());
    }

    @Test
    void effectiveStats_readBaseFromEcs_withModifier() {
        GamePieceStats stats = GamePieceStats.getMonsterStats(2, 5, 3, 1, 2);
        MonsterGamePiece piece = new MonsterGamePiece(stats, GamePieceType.MONSTER, PieceAlignment.P1, UUID.randomUUID(), null);
        Entity entity = engine.createEntity();
        entity.add(new StatsComponent().set(2, 5, 3, 1, 2));
        engine.addEntity(entity);
        piece.setEntity(entity);

        StatsModifier mod = new StatsModifier();
        mod.addDamage = 2;
        piece.getStatsAccumulator().add(mod);
        entity.getComponent(StatsComponent.class).damage = 3;

        assertEquals(5, piece.getEffectiveDamage());
    }
}
