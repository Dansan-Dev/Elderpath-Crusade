package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceAttackedEvent;
import io.github.elderpath_crusade.events.PieceKilledEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;

/**
 * Processes AttackIntentComponent: resolves damage via CombatSystem,
 * emits PieceAttackedEvent (and PieceKilledEvent if target dies), removes intent.
 * Death cleanup (PieceDiedEvent, board removal, engine removal) is handled by DeathSystem.
 */
public class AttackSystem extends EntitySystem {

    private final ComponentMapper<AttackIntentComponent> intentMapper = ComponentMapper.getFor(AttackIntentComponent.class);
    private final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private final ComponentMapper<AlignmentComponent> alignMapper = ComponentMapper.getFor(AlignmentComponent.class);
    private final ComponentMapper<IdentityComponent> idMapper = ComponentMapper.getFor(IdentityComponent.class);
    private final ComponentMapper<StatsComponent> statsMapper = ComponentMapper.getFor(StatsComponent.class);
    private Family family;

    @Override
    public void addedToEngine(Engine engine) {
        family = Family.all(AttackIntentComponent.class, PositionComponent.class, StatsComponent.class).get();
    }

    @Override
    public void update(float deltaTime) {
        ImmutableArray<Entity> entities = getEngine().getEntitiesFor(family);
        if (entities.size() == 0) return;

        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity entity = entities.get(i);
            processAttack(entity);
        }
    }

    /**
     * Execute an attack synchronously (for abilities/input that need immediate resolution).
     */
    public boolean executeAttack(Entity attacker, int targetRow, int targetCol) {
        if (isBlockedByOncePerTurnAbility(attacker)) return false;

        GridIndexSystem gridIndex = getEngine().getSystem(GridIndexSystem.class);
        Entity defender = (gridIndex != null) ? gridIndex.getEntityAt(targetRow, targetCol) : null;
        if (defender == null) return false;

        PositionComponent attackerPos = posMapper.get(attacker);
        AlignmentComponent attackerAlign = alignMapper.get(attacker);
        IdentityComponent attackerId = idMapper.get(attacker);
        IdentityComponent defenderId = idMapper.get(defender);
        PositionComponent defenderPos = posMapper.get(defender);
        StatsComponent defStats = statsMapper.get(defender);

        int damage = io.github.elderpath_crusade.ecs.EntityUtils.getDamage(attacker);

        CombatSystem combat = getEngine().getSystem(CombatSystem.class);
        boolean died = (combat != null) && combat.resolveAttack(attacker, defender, damage);

        String aId = (attackerId != null) ? attackerId.id : "";
        PieceAlignment aOwner = (attackerAlign != null) ? attackerAlign.alignment : PieceAlignment.NEUTRAL;
        String dId = (defenderId != null) ? defenderId.id : "";
        int dRow = (defenderPos != null) ? defenderPos.row : targetRow;
        int dCol = (defenderPos != null) ? defenderPos.col : targetCol;

        TypedEventBus.get().emit(new PieceAttackedEvent(aId, aOwner,
                attackerPos != null ? attackerPos.row : 0, attackerPos != null ? attackerPos.col : 0,
                dId, dRow, dCol, damage));

        if (died) {
            int excessDamage = (defStats != null) ? Math.max(0, -defStats.currentHealth) : 0;
            TypedEventBus.get().emit(new PieceKilledEvent(aId, dId, excessDamage, dRow, dCol));
            // DeathSystem handles PieceDiedEvent, board removal, and engine removal
        }
        return true;
    }

    private void processAttack(Entity attacker) {
        AttackIntentComponent intent = intentMapper.get(attacker);

        if (isBlockedByOncePerTurnAbility(attacker)) {
            attacker.remove(AttackIntentComponent.class);
            return;
        }

        PositionComponent attackerPos = posMapper.get(attacker);
        AlignmentComponent attackerAlign = alignMapper.get(attacker);
        IdentityComponent attackerId = idMapper.get(attacker);

        int targetRow = intent.targetRow;
        int targetCol = intent.targetCol;

        // Find defender entity at target position
        GridIndexSystem gridIndex = getEngine().getSystem(GridIndexSystem.class);
        Entity defender = (gridIndex != null) ? gridIndex.getEntityAt(targetRow, targetCol) : null;

        if (defender == null) {
            attacker.remove(AttackIntentComponent.class);
            return;
        }

        IdentityComponent defenderId = idMapper.get(defender);
        PositionComponent defenderPos = posMapper.get(defender);
        StatsComponent defStats = statsMapper.get(defender);

        // Resolve damage — use effective damage (includes modifiers)
        int damage = io.github.elderpath_crusade.ecs.EntityUtils.getDamage(attacker);
        CombatSystem combat = getEngine().getSystem(CombatSystem.class);
        boolean died = (combat != null) && combat.resolveAttack(attacker, defender, damage);

        // Emit attack event
        String aId = (attackerId != null) ? attackerId.id : "";
        PieceAlignment aOwner = (attackerAlign != null) ? attackerAlign.alignment : PieceAlignment.NEUTRAL;
        String dId = (defenderId != null) ? defenderId.id : "";
        int dRow = (defenderPos != null) ? defenderPos.row : targetRow;
        int dCol = (defenderPos != null) ? defenderPos.col : targetCol;

        TypedEventBus.get().emit(new PieceAttackedEvent(
                aId, aOwner, attackerPos.row, attackerPos.col,
                dId, dRow, dCol, damage));

        if (died) {
            int excessDamage = (defStats != null) ? Math.max(0, -defStats.currentHealth) : 0;
            TypedEventBus.get().emit(new PieceKilledEvent(aId, dId, excessDamage, dRow, dCol));
            // DeathSystem handles PieceDiedEvent, board removal, and engine removal
        }

        // Remove intent
        attacker.remove(AttackIntentComponent.class);
    }

    private boolean isBlockedByOncePerTurnAbility(Entity attacker) {
        io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent aic =
            attacker.getComponent(io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent.class);
        if (aic == null) return false;
        for (io.github.elderpath_crusade.abilities.data.AbilityDefinition def : aic.definitions) {
            if (!"OncePerTurnAttack".equals(def.id())) continue;
            java.util.Map<String, Object> state = aic.state.get(def.id());
            if (state == null) continue;
            Object val = state.get("attackedThisTurn");
            if (val instanceof Number n && n.intValue() != 0) return true;
            if (Boolean.TRUE.equals(val)) return true;
        }
        return false;
    }
}
