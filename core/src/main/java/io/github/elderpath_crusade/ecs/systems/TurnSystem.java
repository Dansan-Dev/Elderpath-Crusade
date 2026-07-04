package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.ComputedStatsComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.TurnStateComponent;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;

/**
 * Owns turn state via TurnStateComponent on a singleton entity.
 * Resets actions for current player's pieces on turn start.
 * Provides methods called by TurnManager facade.
 */
public class TurnSystem extends EntitySystem {
    private final ComponentMapper<StatsComponent> statsMapper = ComponentMapper.getFor(StatsComponent.class);
    private final ComponentMapper<AlignmentComponent> alignMapper = ComponentMapper.getFor(AlignmentComponent.class);
    private final ComponentMapper<ComputedStatsComponent> computedStatsMapper = ComponentMapper.getFor(ComputedStatsComponent.class);
    private Family pieceFamily;

    private Entity stateEntity;
    private TurnStateComponent state;

    @Override
    public void addedToEngine(Engine engine) {
        this.pieceFamily = Family.all(StatsComponent.class, AlignmentComponent.class).get();

        stateEntity = engine.createEntity();
        state = new TurnStateComponent();
        stateEntity.add(state);
        engine.addEntity(stateEntity);
    }

    @Override
    public void update(float deltaTime) {
        // No per-frame work; turn transitions are method-driven
    }

    public PieceAlignment getCurrentPlayer() {
        return state.currentPlayer;
    }

    public boolean isStarted() {
        return state.started;
    }

    public boolean isWaitingForNextPlayer() {
        return state.waitingForNextPlayer;
    }

    public void startIfNeeded() {
        if (state.started) return;
        state.started = true;
        state.currentPlayer = PieceAlignment.P1;
        state.turnCount = 1;
        doStartTurn(state.currentPlayer);
    }

    public void endTurn() {
        if (!state.started) return;
        GameContext.get().getPlayerManager().onEndTurn(state.currentPlayer);
        TypedEventBus.get().emit(new TurnEndedEvent(state.currentPlayer));

        state.currentPlayer = (state.currentPlayer == PieceAlignment.P1)
                ? PieceAlignment.P2
                : PieceAlignment.P1;
        state.turnCount++;

        if (GameContext.get().getGameModeManager().getCurrent() == GameMode.LOCAL_MATCH) {
            state.waitingForNextPlayer = true;
        } else {
            doStartTurn(state.currentPlayer);
        }
    }

    public void startNextPlayerTurn() {
        if (!state.waitingForNextPlayer) return;
        state.waitingForNextPlayer = false;
        GameContext.get().getPlayerManager().initializeIfNeeded();
        doStartTurn(state.currentPlayer);
    }

    public void reset() {
        state.started = false;
        state.currentPlayer = PieceAlignment.P1;
        state.turnCount = 0;
        state.waitingForNextPlayer = false;
        GameContext.get().getPlayerManager().resetForNewGame();
    }

    private void doStartTurn(PieceAlignment player) {
        GameContext.get().getPlayerManager().initializeIfNeeded();
        GameContext.get().getPlayerManager().onStartTurn(player);
        resetActionsForPlayer(player);
        TypedEventBus.get().emit(new TurnStartedEvent(player));
    }

    private void resetActionsForPlayer(PieceAlignment player) {
        ImmutableArray<Entity> entities = getEngine().getEntitiesFor(pieceFamily);
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            AlignmentComponent align = alignMapper.get(e);
            if (align.alignment == player) {
                StatsComponent stats = statsMapper.get(e);
                ComputedStatsComponent computed = computedStatsMapper.get(e);
                stats.remainingActions = (computed != null) ? computed.actions : stats.actions;
            }
        }
    }
}
