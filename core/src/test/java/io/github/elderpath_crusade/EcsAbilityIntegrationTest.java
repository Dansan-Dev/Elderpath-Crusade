package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.components.AbilityComponent;
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

class EcsAbilityIntegrationTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
    }

    @Test
    void abilityComponent_add_storesName() {
        AbilityComponent ac = new AbilityComponent();
        ac.add("TestAbility");
        assertTrue(ac.abilityNames.contains("TestAbility"));
    }

    @Test
    void abilityComponent_clear_removesAll() {
        AbilityComponent ac = new AbilityComponent();
        ac.add("TestAbility");
        ac.clear();
        assertTrue(ac.abilityNames.isEmpty());
    }

    @Test
    void monsterGamePiece_getAbilities_returnsEmptyList() {
        GamePieceStats stats = GamePieceStats.getMonsterStats(1, 5, 3, 1, 2);
        MonsterGamePiece piece = new MonsterGamePiece(stats, GamePieceType.MONSTER, PieceAlignment.P1, UUID.randomUUID(), null);
        assertTrue(piece.getAbilities().isEmpty());
    }
}
