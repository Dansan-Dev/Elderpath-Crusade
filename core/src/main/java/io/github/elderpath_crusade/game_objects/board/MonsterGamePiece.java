package io.github.elderpath_crusade.game_objects.board;

import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.abilities.stats.StatsAccumulator;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.ecs.components.ModifierComponent;
import io.github.elderpath_crusade.ecs.components.StunComponent;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.model.piece.PieceModel;
import io.github.elderpath_crusade.model.piece.PieceStats;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MonsterGamePiece extends GamePiece {

    private record BoardContext(Board board, Board.Position position) {}

    private final StatsAccumulator statsAccumulator = new StatsAccumulator();
    @Getter
    private Entity entity;
    @Getter
    private final PieceModel pieceModel;

    public MonsterGamePiece(GamePieceStats stats, GamePieceType type, PieceAlignment alignment, UUID id, Renderable sprite) {
        super(stats, type, alignment, id, sprite);
        if (type.equals(GamePieceType.TERRAIN)) throw new IllegalArgumentException("Cannot create a monster as terrain");
        this.pieceModel = new PieceModel(
                id.toString(), type.name(), alignment,
                new PieceStats(stats.getCost(), stats.getMaxHealth(), stats.getDamage(), stats.getSpeed(), stats.getActions()));
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
        if (entity != null) {
            stats.linkEntity(entity);
        }
    }

    public StatsAccumulator getStatsAccumulator() {
        if (entity != null) {
            ModifierComponent mc = entity.getComponent(ModifierComponent.class);
            if (mc != null) return mc.accumulator;
        }
        return statsAccumulator;
    }

    public io.github.elderpath_crusade.ecs.components.PositionComponent getPositionComponent() {
        if (entity == null) return null;
        return entity.getComponent(io.github.elderpath_crusade.ecs.components.PositionComponent.class);
    }

    // ---- Abilities API (stubs — OOP abilities removed, data-driven system is authoritative) ----
    public void addAbility(Ability ability) { /* no-op */ }
    public void removeAbility(Ability ability) { /* no-op */ }
    public List<Ability> getAbilities() { return Collections.emptyList(); }
    public void detachAllAbilities() { /* no-op */ }

    public void notifySpawned(int row, int col) { /* no-op */ }
    public void notifyMoved(int fromRow, int fromCol, int toRow, int toCol) { /* no-op */ }
    public void notifyAttack(MonsterGamePiece target, int damage) { /* no-op */ }
    public void notifyDamaged(int amount, MonsterGamePiece source) { /* no-op */ }
    public void notifyDied() { /* no-op */ }
    public void notifyTurnStarted(PieceAlignment currentPlayer) { /* no-op */ }
    public void notifyTurnEnded(PieceAlignment endingPlayer) { /* no-op */ }

    public GamePieceStats getEffectiveStats() {
        return GamePieceStats.getMonsterStats(
            getEffectiveCost(), getEffectiveMaxHealth(), getEffectiveDamage(), getEffectiveSpeed(), getEffectiveActions()
        );
    }

    // ---- Effective stats (base + accumulated modifiers) ----
    public int getEffectiveDamage() {
        if (entity != null) {
            io.github.elderpath_crusade.ecs.components.ComputedStatsComponent c = entity.getComponent(io.github.elderpath_crusade.ecs.components.ComputedStatsComponent.class);
            if (c != null) return c.damage;
        }
        int base = getStats().getDamage();
        int add = 0; float mult = 0f;
        for (StatsModifier m : getStatsAccumulator().getAll()) { add += m.addDamage; mult += m.multDamage; }
        return StatsModifier.applyInt(base, add, mult);
    }

    public int getEffectiveSpeed() {
        if (entity != null) {
            io.github.elderpath_crusade.ecs.components.ComputedStatsComponent c = entity.getComponent(io.github.elderpath_crusade.ecs.components.ComputedStatsComponent.class);
            if (c != null) return c.speed;
        }
        int base = getStats().getSpeed();
        int add = 0; float mult = 0f;
        for (StatsModifier m : getStatsAccumulator().getAll()) { add += m.addSpeed; mult += m.multSpeed; }
        return StatsModifier.applyInt(base, add, mult);
    }

    public int getEffectiveActions() {
        if (entity != null) {
            io.github.elderpath_crusade.ecs.components.ComputedStatsComponent c = entity.getComponent(io.github.elderpath_crusade.ecs.components.ComputedStatsComponent.class);
            if (c != null) return c.actions;
        }
        int base = getStats().getActions();
        int add = 0; float mult = 0f;
        for (StatsModifier m : getStatsAccumulator().getAll()) { add += m.addActions; mult += m.multActions; }
        return StatsModifier.applyInt(base, add, mult);
    }

    public int getEffectiveMaxHealth() {
        if (entity != null) {
            io.github.elderpath_crusade.ecs.components.ComputedStatsComponent c = entity.getComponent(io.github.elderpath_crusade.ecs.components.ComputedStatsComponent.class);
            if (c != null) return c.maxHealth;
        }
        int base = getStats().getMaxHealth();
        int add = 0; float mult = 0f;
        for (StatsModifier m : getStatsAccumulator().getAll()) { add += m.addMaxHealth; mult += m.multMaxHealth; }
        return StatsModifier.applyInt(base, add, mult);
    }

    public int getEffectiveCost() {
        if (entity != null) {
            io.github.elderpath_crusade.ecs.components.ComputedStatsComponent c = entity.getComponent(io.github.elderpath_crusade.ecs.components.ComputedStatsComponent.class);
            if (c != null) return c.cost;
        }
        int base = getStats().getCost();
        int add = 0; float mult = 0f;
        for (StatsModifier m : getStatsAccumulator().getAll()) { add += m.addCost; mult += m.multCost; }
        return StatsModifier.applyInt(base, add, mult);
    }

    public int getEffectiveRange() {
        if (entity != null) {
            io.github.elderpath_crusade.ecs.components.ComputedStatsComponent c = entity.getComponent(io.github.elderpath_crusade.ecs.components.ComputedStatsComponent.class);
            if (c != null) return c.range;
        }
        int base = 0;
        int add = 0; float mult = 0f;
        for (StatsModifier m : getStatsAccumulator().getAll()) { add += m.addRange; mult += m.multRange; }
        return StatsModifier.applyInt(base, add, mult);
    }

    public boolean ignoresTerrainAsBlockers() {
        if (entity != null) {
            io.github.elderpath_crusade.ecs.components.ComputedStatsComponent c = entity.getComponent(io.github.elderpath_crusade.ecs.components.ComputedStatsComponent.class);
            if (c != null) return c.ignoreTerrainAsBlockers;
        }
        for (StatsModifier m : getStatsAccumulator().getAll()) if (m.ignoreTerrainAsBlockers) return true;
        return false;
    }

    public boolean ignoresFriendlyUnitsAsBlockers() {
        if (entity != null) {
            io.github.elderpath_crusade.ecs.components.ComputedStatsComponent c = entity.getComponent(io.github.elderpath_crusade.ecs.components.ComputedStatsComponent.class);
            if (c != null) return c.ignoreFriendlyAsBlockers;
        }
        for (StatsModifier m : getStatsAccumulator().getAll()) if (m.ignoreFriendlyUnitsAsBlockers) return true;
        return false;
    }

    public boolean ignoresHostileUnitsAsBlockers() {
        if (entity != null) {
            io.github.elderpath_crusade.ecs.components.ComputedStatsComponent c = entity.getComponent(io.github.elderpath_crusade.ecs.components.ComputedStatsComponent.class);
            if (c != null) return c.ignoreHostileAsBlockers;
        }
        for (StatsModifier m : getStatsAccumulator().getAll()) if (m.ignoreHostileUnitsAsBlockers) return true;
        return false;
    }

    public boolean heal(int amount) {
        if (amount <= 0) return false;
        int currentHealth = getStats().getCurrentHealth();
        int maxHealth = getEffectiveMaxHealth();
        if (currentHealth >= maxHealth) return false;
        int newHealth = Math.min(currentHealth + amount, maxHealth);
        getStats().setCurrentHealth(newHealth);
        return newHealth > currentHealth;
    }

    public void die() {
        Optional<BoardContext> context = getBoardContext();
        if (context.isEmpty()) return;
        BoardContext ctx = context.get();
        Board.Position pos = ctx.position;
        Board board = ctx.board;
        board.removeGamePieceAtPos(pos.getRow(), pos.getCol());
    }

    private Optional<BoardContext> getBoardContext() {
        Object posObj = getData(GamePieceData.POSITION);
        if (!(posObj instanceof Board.Position pos)) return Optional.empty();
        Board board = pos.getBoard();
        if (board == null) return Optional.empty();
        return Optional.of(new BoardContext(board, pos));
    }

    public boolean isStunned() {
        if (entity != null) {
            StunComponent stun = entity.getComponent(StunComponent.class);
            if (stun != null) return stun.isStunned();
        }
        Object stunObj = getData(GamePieceData.STUN_TURNS_REMAINING);
        if (stunObj instanceof Integer stunTurns) return stunTurns > 0;
        return false;
    }

    public boolean isExhausted() {
        return getStats().getRemainingActions() == 0;
    }
}
