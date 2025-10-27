package io.github.forest_of_dreams.game_objects.board;
import io.github.forest_of_dreams.abilities.Ability;
import io.github.forest_of_dreams.abilities.stats.StatsAccumulator;
import io.github.forest_of_dreams.abilities.stats.StatsModifier;
import io.github.forest_of_dreams.abilities.TriggeredAbility;
import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.settings.GamePieceType;
import io.github.forest_of_dreams.interfaces.Renderable;
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

    // Generic interaction triggered by Plot: move this piece one step upwards if possible
    public void expendAction() {
        Optional<BoardContext> context = getBoardContext();
        if (context.isEmpty()) return;

        BoardContext ctx = context.get();
        Board.Position pos = ctx.position;
        Board board = ctx.board;
        int currentRow = pos.getRow();
        int currentCol = pos.getCol();

        boolean isMovablePiece = !type.equals(GamePieceType.TERRAIN) && alignment.equals(PieceAlignment.P1);
        if (!isMovablePiece) return;

        int newRow = currentRow + 1; // upwards
        boolean isValidDestination = ctx.position.isValid(newRow, currentCol);
        if (!isValidDestination) return;

        GamePiece gamePiece = board.getGamePieceAtPos(newRow, currentCol);
        if (gamePiece != null && gamePiece.type.equals(GamePieceType.TERRAIN)) return;
        else if (board.getGamePieceAtPos(newRow, currentCol) instanceof MonsterGamePiece mgp) {
            if (mgp.alignment.equals(PieceAlignment.P2)) attack();
        }
        else {
            moveUpOneStep();
        }
    }

    private void moveUpOneStep() {
        Optional<BoardContext> context = getBoardContext();
        if (context.isEmpty()) return;

        BoardContext ctx = context.get();
        Board.Position pos = ctx.position;
        Board board = ctx.board;
        int currentRow = pos.getRow();
        int currentCol = pos.getCol();

        int newRow = currentRow + 1;
        if (!pos.isValid(newRow, currentCol)) return;
        board.moveGamePiece(currentRow, currentCol, newRow, currentCol);
        pos.setRow(newRow);
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
