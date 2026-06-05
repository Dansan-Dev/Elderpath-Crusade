package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceAttackedEvent;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;

/**
 * Processes AttackIntentComponent: resolves damage via CombatSystem,
 * emits PieceAttackedEvent (and PieceDiedEvent if target dies), removes intent.
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
        GridIndexSystem gridIndex = getEngine().getSystem(GridIndexSystem.class);
        Entity defender = (gridIndex != null) ? gridIndex.getEntityAt(targetRow, targetCol) : null;
        if (defender == null) return false;

        PositionComponent attackerPos = posMapper.get(attacker);
        AlignmentComponent attackerAlign = alignMapper.get(attacker);
        IdentityComponent attackerId = idMapper.get(attacker);
        IdentityComponent defenderId = idMapper.get(defender);
        PositionComponent defenderPos = posMapper.get(defender);

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
            TypedEventBus.get().emit(new PieceDiedEvent(dId, dRow, dCol));
            Board board = GameContext.get().getActiveBoard();
            if (board != null) board.removeGamePieceAtPos(dRow, dCol);
        }
        return true;
    }

    private void processAttack(Entity attacker) {
        AttackIntentComponent intent = intentMapper.get(attacker);
        PositionComponent attackerPos = posMapper.get(attacker);
        AlignmentComponent attackerAlign = alignMapper.get(attacker);
        IdentityComponent attackerId = idMapper.get(attacker);
        StatsComponent attackerStats = statsMapper.get(attacker);

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

        // Handle death
        if (died) {
            TypedEventBus.get().emit(new PieceDiedEvent(dId, dRow, dCol));
            Board board = GameContext.get().getActiveBoard();
            if (board != null) {
                board.removeGamePieceAtPos(dRow, dCol);
            }
        }

        // Remove intent
        attacker.remove(AttackIntentComponent.class);
    }
}
