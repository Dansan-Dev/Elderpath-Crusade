package io.github.elderpath_crusade.ui_objects;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.ActionableAbilityExecutor;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ActionDef;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.StunComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.input.InteractionManager;
import io.github.elderpath_crusade.supers.HigherOrderUI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AbilityPopup extends HigherOrderUI {

    private static final int BUBBLE_Z = 20;
    private static final int BUBBLE_Y_OFFSET = 8;

    private static final ComponentMapper<AbilityInstanceComponent> ABILITY_M =
            ComponentMapper.getFor(AbilityInstanceComponent.class);
    private static final ComponentMapper<PositionComponent> POSITION_M =
            ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<AlignmentComponent> ALIGNMENT_M =
            ComponentMapper.getFor(AlignmentComponent.class);
    private static final ComponentMapper<StatsComponent> STATS_M =
            ComponentMapper.getFor(StatsComponent.class);
    private static final ComponentMapper<StunComponent> STUN_M =
            ComponentMapper.getFor(StunComponent.class);
    private static final ComponentMapper<IdentityComponent> IDENTITY_M =
            ComponentMapper.getFor(IdentityComponent.class);

    private static final Family FAMILY = Family.all(
            AbilityInstanceComponent.class,
            PositionComponent.class,
            AlignmentComponent.class,
            StatsComponent.class
    ).get();

    private final Map<String, AbilityBubble> activeBubbles = new HashMap<>();

    public AbilityPopup() {
        super();
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        GameContext ctx = GameContext.get();
        if (ctx == null) return;
        Board board = ctx.getActiveBoard();
        if (board == null) return;

        InteractionManager interactionManager = ctx.getInteractionManager();

        if (interactionManager.hasActiveSelection()) {
            removeAllBubbles(interactionManager);
            return;
        }

        PieceAlignment currentPlayer = ctx.getTurnManager().getCurrentPlayer();
        ImmutableArray<Entity> entities = ctx.getEcsEngine().getEntitiesFor(FAMILY);

        Set<String> desiredKeys = new HashSet<>();
        List<BubbleSpec> specs = new ArrayList<>();

        for (Entity entity : entities) {
            AlignmentComponent alignment = ALIGNMENT_M.get(entity);
            if (alignment.alignment != currentPlayer) continue;

            StatsComponent stats = STATS_M.get(entity);
            if (stats.remainingActions <= 0) continue;

            StunComponent stun = STUN_M.get(entity);
            if (stun != null && stun.isStunned()) continue;

            AbilityInstanceComponent abilityComp = ABILITY_M.get(entity);
            IdentityComponent identity = IDENTITY_M.get(entity);
            if (identity == null) continue;

            PositionComponent pos = POSITION_M.get(entity);

            int abilityIndex = 0;
            for (AbilityDefinition def : abilityComp.definitions) {
                if (def.actions() == null || def.actions().isEmpty()) continue;

                String key = identity.id + "_" + abilityIndex;
                desiredKeys.add(key);
                specs.add(new BubbleSpec(key, entity, def, abilityIndex, pos));
                abilityIndex++;
            }
        }

        Set<String> staleKeys = new HashSet<>(activeBubbles.keySet());
        staleKeys.removeAll(desiredKeys);
        for (String key : staleKeys) {
            interactionManager.removeClickable(activeBubbles.get(key));
            activeBubbles.remove(key);
        }

        int bubbleSize = (int) (board.getPLOT_WIDTH() * 0.7f);
        int boardX = board.getBounds().getX();
        int boardY = board.getBounds().getY();

        for (BubbleSpec spec : specs) {
            if (!activeBubbles.containsKey(spec.key)) {
                int plotX = boardX + spec.pos.col * board.getPLOT_WIDTH();
                int plotY = boardY + spec.pos.row * board.getPLOT_HEIGHT();
                int bubX = plotX + board.getPLOT_WIDTH() / 2 - bubbleSize / 2;
                int bubY = plotY + board.getPLOT_HEIGHT() + BUBBLE_Y_OFFSET;

                AbilityDefinition capturedDef = spec.def;
                Entity capturedEntity = spec.entity;
                ActionDef capturedAction = capturedDef.actions().get(0);

                AbilityBubble bubble = new AbilityBubble(bubX, bubY, bubbleSize, BUBBLE_Z)
                        .withIndexLabel(spec.abilityIndex + 1, Color.WHITE)
                        .withOnClick(
                                (e) -> ActionableAbilityExecutor.execute(capturedEntity, capturedDef, capturedAction),
                                ClickableEffectData.getImmediate()
                        );

                activeBubbles.put(spec.key, bubble);
                interactionManager.addClickable(bubble);
            }
        }

        for (AbilityBubble bubble : activeBubbles.values()) {
            bubble.renderUI(batch, isPaused);
        }
    }

    private void removeAllBubbles(InteractionManager interactionManager) {
        for (AbilityBubble bubble : activeBubbles.values()) {
            interactionManager.removeClickable(bubble);
        }
        activeBubbles.clear();
    }

    private record BubbleSpec(String key, Entity entity, AbilityDefinition def, int abilityIndex, PositionComponent pos) {}
}
