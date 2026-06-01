package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.abilities.AbilityRelay;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.ecs.components.AbilityComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EcsAbilityIntegrationTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
    }

    @AfterEach
    void tearDown() {
        AbilityRelay.stop();
    }

    @Test
    void abilityComponent_addAbility_storesInstance() {
        AbilityComponent ac = new AbilityComponent();
        Ability mockAbility = mock(Ability.class);
        when(mockAbility.getName()).thenReturn("TestAbility");

        ac.addAbility(mockAbility);

        assertTrue(ac.getAbilities().contains(mockAbility));
        assertTrue(ac.abilityNames.contains("TestAbility"));
    }

    @Test
    void abilityComponent_removeAbility_removesInstance() {
        AbilityComponent ac = new AbilityComponent();
        Ability mockAbility = mock(Ability.class);
        when(mockAbility.getName()).thenReturn("TestAbility");

        ac.addAbility(mockAbility);
        ac.removeAbility(mockAbility);

        assertTrue(ac.getAbilities().isEmpty());
        assertTrue(ac.abilityNames.isEmpty());
    }

    @Test
    void monsterGamePiece_addAbility_syncsToAbilityComponent() {
        GamePieceStats stats = GamePieceStats.getMonsterStats(1, 5, 3, 1, 2);
        MonsterGamePiece piece = new MonsterGamePiece(stats, GamePieceType.MONSTER, PieceAlignment.P1, UUID.randomUUID(), null);

        Entity entity = engine.createEntity();
        entity.add(new StatsComponent().set(1, 5, 3, 1, 2));
        entity.add(new AbilityComponent());
        engine.addEntity(entity);
        piece.setEntity(entity);

        Ability mockAbility = mock(Ability.class);
        when(mockAbility.getName()).thenReturn("TestAbility");
        piece.addAbility(mockAbility);

        AbilityComponent ac = entity.getComponent(AbilityComponent.class);
        assertTrue(ac.getAbilities().contains(mockAbility));
    }

    @Test
    void monsterGamePiece_removeAbility_syncsToAbilityComponent() {
        GamePieceStats stats = GamePieceStats.getMonsterStats(1, 5, 3, 1, 2);
        MonsterGamePiece piece = new MonsterGamePiece(stats, GamePieceType.MONSTER, PieceAlignment.P1, UUID.randomUUID(), null);

        Entity entity = engine.createEntity();
        entity.add(new StatsComponent().set(1, 5, 3, 1, 2));
        entity.add(new AbilityComponent());
        engine.addEntity(entity);
        piece.setEntity(entity);

        Ability mockAbility = mock(Ability.class);
        when(mockAbility.getName()).thenReturn("TestAbility");
        piece.addAbility(mockAbility);
        piece.removeAbility(mockAbility);

        AbilityComponent ac = entity.getComponent(AbilityComponent.class);
        assertFalse(ac.getAbilities().contains(mockAbility));
    }

    @Test
    void abilityRelay_forwardsEventToTriggeredAbility() {
        TriggeredAbility mockTriggered = mock(TriggeredAbility.class);
        when(mockTriggered.getName()).thenReturn("TestTriggered");

        Entity entity = engine.createEntity();
        AbilityComponent ac = new AbilityComponent();
        ac.addAbility(mockTriggered);
        entity.add(ac);
        engine.addEntity(entity);

        AbilityRelay.startIfNeeded();
        TurnStartedEvent event = new TurnStartedEvent(PieceAlignment.P1);
        TypedEventBus.get().emit(event);

        verify(mockTriggered).onGameEvent(event);
    }
}
