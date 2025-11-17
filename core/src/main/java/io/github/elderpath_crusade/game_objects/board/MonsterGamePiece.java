package io.github.elderpath_crusade.game_objects.board;
import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.abilities.stats.StatsAccumulator;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.interfaces.Renderable;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class MonsterGamePiece extends GamePiece {

    private record BoardContext(Board board, Board.Position position) {}

    // Container for this piece's abilities (defined by concrete piece classes)
    private final List<Ability> abilities = new ArrayList<>();
    // Accumulator of all modifiers affecting this piece (local + auras from others)
    @Getter
    private final StatsAccumulator statsAccumulator = new StatsAccumulator();

    public MonsterGamePiece(GamePieceStats stats, GamePieceType type, PieceAlignment alignment, UUID id, Renderable sprite) {
        super(stats, type, alignment, id, sprite);
        if (type.equals(GamePieceType.TERRAIN)) throw new IllegalArgumentException("Cannot create a monster as terrain");
    }

    // ---- Abilities API ----
    public void addAbility(Ability ability) {
        if (ability == null) return;
        abilities.add(ability);
        ability.onAttach(this);
    }

    public void removeAbility(Ability ability) {
        if (ability == null) return;
        if (abilities.remove(ability)) {
            try { ability.onDetach(); } catch (Exception ignored) {}
        }
    }

    public List<Ability> getAbilities() {
        return Collections.unmodifiableList(abilities);
    }

    private void forEachTriggered(Consumer<TriggeredAbility> action) {
        if (abilities.isEmpty()) return;
        abilities.stream()
            .filter(ability -> ability instanceof TriggeredAbility)
            .map(ability -> (TriggeredAbility) ability)
            .forEach((triggeredAbility) -> {
                try { action.accept(triggeredAbility);} catch (Exception ignored){}
            });
    }

    public void notifySpawned(int row, int col) {
        forEachTriggered(a -> a.onOwnerSpawned(this, row, col));
    }

    public void notifyMoved(int fromRow, int fromCol, int toRow, int toCol) {
        forEachTriggered(a -> a.onOwnerMoved(this, fromRow, fromCol, toRow, toCol));
    }

    public void notifyAttack(MonsterGamePiece target, int damage) {
        forEachTriggered(a -> a.onOwnerAttack(this, target, damage));
    }

    public void notifyDamaged(int amount, MonsterGamePiece source) {
        forEachTriggered(a -> a.onOwnerDamaged(this, amount, source));
    }

    public void notifyDied() {
        forEachTriggered(a -> a.onOwnerDied(this));
    }

    public void notifyTurnStarted(PieceAlignment currentPlayer) {
        forEachTriggered(a -> a.onTurnStarted(currentPlayer));
    }

    public void notifyTurnEnded(PieceAlignment endingPlayer) {
        forEachTriggered(a -> a.onTurnEnded(endingPlayer));
    }

    private void detachAllAbilities() {
        for (Ability a : abilities) {
            try { a.onDetach(); } catch (Exception ignored) {}
        }
        abilities.clear();
        // Clear any lingering external modifiers targeting this piece
        // External abilities should call StatsModifier.clear(), but as a safety, remove by null source does nothing.
    }

    public GamePieceStats getEffectiveStats() {
        int damage = getEffectiveDamage();
        int speed = getEffectiveSpeed();
        int actions = getEffectiveActions();
        int maxHealth = getEffectiveMaxHealth();
        int cost = getEffectiveCost();
        return GamePieceStats.getMonsterStats(
            cost, maxHealth, damage, speed, actions
        );
    }

    // ---- Effective stats (base + accumulated modifiers) ----
    public int getEffectiveDamage() {
        int base = getStats().getDamage();
        int add = 0; float mult = 0f;
        for (StatsModifier m : statsAccumulator.getAll()) { add += m.addDamage; mult += m.multDamage; }
        return StatsModifier.applyInt(base, add, mult);
    }

    public int getEffectiveSpeed() {
        int base = getStats().getSpeed();
        int add = 0; float mult = 0f;
        for (StatsModifier m : statsAccumulator.getAll()) { add += m.addSpeed; mult += m.multSpeed; }
        return StatsModifier.applyInt(base, add, mult);
    }

    public int getEffectiveActions() {
        int base = getStats().getActions();
        int add = 0; float mult = 0f;
        for (StatsModifier m : statsAccumulator.getAll()) { add += m.addActions; mult += m.multActions; }
        return StatsModifier.applyInt(base, add, mult);
    }

    public int getEffectiveMaxHealth() {
        int base = getStats().getMaxHealth();
        int add = 0; float mult = 0f;
        for (StatsModifier m : statsAccumulator.getAll()) { add += m.addMaxHealth; mult += m.multMaxHealth; }
        return StatsModifier.applyInt(base, add, mult);
    }

    public int getEffectiveCost() {
        int base = getStats().getCost();
        int add = 0; float mult = 0f;
        for (StatsModifier m : statsAccumulator.getAll()) { add += m.addCost; mult += m.multCost; }
        return StatsModifier.applyInt(base, add, mult);
    }

    /**
     * Effective attack range in tiles (cardinal lines). Base is 0 and must be modified by abilities.
     */
    public int getEffectiveRange() {
        int base = 0; // default melee classification
        int add = 0; float mult = 0f;
        for (StatsModifier m : statsAccumulator.getAll()) { add += m.addRange; mult += m.multRange; }
        return StatsModifier.applyInt(base, add, mult);
    }

    public boolean ignoresTerrainAsBlockers() {
        for (StatsModifier m : statsAccumulator.getAll()) if (m.ignoreTerrainAsBlockers) return true;
        return false;
    }

    public boolean ignoresFriendlyUnitsAsBlockers() {
        for (StatsModifier m : statsAccumulator.getAll()) if (m.ignoreFriendlyUnitsAsBlockers) return true;
        return false;
    }

    public boolean ignoresHostileUnitsAsBlockers() {
        for (StatsModifier m : statsAccumulator.getAll()) if (m.ignoreHostileUnitsAsBlockers) return true;
        return false;
    }

    public void attack() {
        Optional<BoardContext> context = getBoardContext();
        if (context.isEmpty()) return;

        BoardContext ctx = context.get();
        Board.Position pos = ctx.position;
        Board board = ctx.board;
        int currentRow = ctx.position.getRow();
        int currentCol = ctx.position.getCol();

        int newRow = currentRow + 1;
        if (!pos.isValid(newRow, currentCol)) return;
        if (!(board.getGamePieceAtPos(newRow, currentCol) instanceof MonsterGamePiece mgp)) return;
        mgp.stats.dealDamage(stats.getDamage());
        if (mgp.stats.getCurrentHealth()<=0) mgp.die();
    }

    /**
     * Heal this piece by the specified amount, capped at max health.
     * @param amount The amount of health to restore
     * @return true if health increased, false if already at max health
     */
    public boolean heal(int amount) {
        if (amount <= 0) return false;
        int currentHealth = getStats().getCurrentHealth();
        int maxHealth = getStats().getMaxHealth();
        if (currentHealth >= maxHealth) {
            return false; // Already at max health
        }
        int newHealth = Math.min(currentHealth + amount, maxHealth);
        getStats().setCurrentHealth(newHealth);
        return newHealth > currentHealth; // Return true if health actually increased
    }

    public void die() {
        Optional<BoardContext> context = getBoardContext();
        if (context.isEmpty()) return;

        // Detach abilities before removing from board
        detachAllAbilities();

        BoardContext ctx = context.get();
        Board.Position pos = ctx.position;
        Board board = ctx.board;
        board.removeGamePieceAtPos(pos.getRow(), pos.getCol());
    }

    private Optional<BoardContext> getBoardContext() {
        Object posObj = getData(GamePieceData.POSITION);
        if (!(posObj instanceof Board.Position pos)) {
            return Optional.empty();
        }

        Board board = pos.getBoard();
        if (board == null) {
            return Optional.empty();
        }

        return Optional.of(new BoardContext(board, pos));
    }
}
