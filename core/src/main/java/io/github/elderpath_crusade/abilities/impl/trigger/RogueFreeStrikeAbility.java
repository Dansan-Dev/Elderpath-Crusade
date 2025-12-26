package io.github.elderpath_crusade.abilities.impl.trigger;

import io.github.elderpath_crusade.utils.AbilityUtils;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.plot.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.managers.InteractionManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.managers.TurnManager;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Rogue passive/triggered: After each manual move, immediately allow an
 * optional free attack
 * on a single valid target in range. The attack does not cost an action; the
 * movement still does.
 */
public class RogueFreeStrikeAbility implements TriggeredAbility {
    private MonsterGamePiece owner;

    @Override
    public String getName() {
        return "Free Strike";
    }

    @Override
    public String getDescription() {
        return RogueFreeStrikeAbility.getAbilityDescription();
    }

    public static String getAbilityDescription() {
        return "After moving: you may make a free attack.";
    }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public void onDetach() {
        this.owner = null;
    }

    @Override
    public void onOwnerMoved(MonsterGamePiece owner, int fromRow, int fromCol, int toRow, int toCol) {
        if (this.owner == null || owner != this.owner)
            return;
        // Only trigger for manual moves
        Object cause = owner.getData(GamePieceData.MOVE_CAUSE);
        if (!(cause instanceof String s) || !"MANUAL".equals(s)) {
            return;
        }
        // Clear the marker immediately to avoid leaking into other logic
        owner.updateData(GamePieceData.MOVE_CAUSE, null);
        // Find board context
        Object posObj = owner.getData(GamePieceData.POSITION);
        if (!(posObj instanceof Board.Position pos))
            return;
        Board board = pos.getBoard();
        if (board == null)
            return;
        // Compute attackable enemy plots from the new position using owner's effective
        // range
        List<Plot> attackables = board.getAttackableEnemyPlots(toRow, toCol, owner.getAlignment());
        if (attackables == null || attackables.isEmpty())
            return; // per UX: no selection if no valid targets

        // Bot path vs Human path delegated to sub-methods for clarity
        boolean botControlled = owner.getAlignment() == PieceAlignment.P2
                && SettingsManager.debug.enableP2Bot
                && TurnManager.getCurrentPlayer() == owner.getAlignment();
        if (botControlled) {
            handleBotFreeStrike(board, toRow, toCol, attackables);
            return;
        }
        startHumanFreeStrikeSelection(board, owner, toRow, toCol, attackables);
    }

    // --- Sub-methods: split bot and human paths for clarity ---
    private void handleBotFreeStrike(Board board, int toRow, int toCol, List<Plot> attackables) {
        if (this.owner == null || board == null || attackables == null || attackables.isEmpty())
            return;
        Plot best = null;
        MonsterGamePiece bestEnemy = null;
        int bestEnemyRow = -1, bestEnemyCol = -1;
        int bestScore = Integer.MIN_VALUE;
        int dmg = owner.getEffectiveDamage();
        for (Plot p : attackables) {
            int[] dIdx = p.getIndices();
            if (dIdx == null)
                continue;
            GamePiece gp = board.getGamePieceAtPos(dIdx[0], dIdx[1]);
            if (!(gp instanceof MonsterGamePiece enemy))
                continue;
            int hp = Math.max(0, enemy.getStats().getCurrentHealth());
            boolean lethal = dmg >= hp;
            int score = (lethal ? 1000 : 0)
                    + Math.min(10, Math.max(0, enemy.getStats().getCost())) * 5
                    + Math.min(5, Math.max(0, enemy.getEffectiveDamage())) * 2
                    - hp; // prefer lower HP if non-lethal
            if (score > bestScore) {
                bestScore = score;
                best = p;
                bestEnemy = enemy;
                bestEnemyRow = dIdx[0];
                bestEnemyCol = dIdx[1];
            }
        }
        if (best != null) {
            AbilityUtils.performAttack(
                    board, owner, bestEnemy, toRow, toCol, bestEnemyRow, bestEnemyCol);
        }
    }

    private void startHumanFreeStrikeSelection(Board board, MonsterGamePiece owner, int toRow, int toCol,
            List<Plot> attackables) {
        if (board == null || owner == null || attackables == null || attackables.isEmpty())
            return;
        // Use the new InteractionManager.requestPick API to initiate a one-shot
        // selection without a surrogate Clickable.
        ClickableEffectData data = ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1);
        // Create a TargetFilter that validates targets and provides eligible plots for
        // highlighting
        TargetFilter filter = new TargetFilter() {
            @Override
            public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
                Plot p = resolveToPlot(board, box);
                if (p == null)
                    return false;
                for (Plot allowed : attackables) {
                    if (allowed == p)
                        return true;
                }
                return false;
            }

            @Override
            public List<Plot> getEligibleTargets(int targetIndex) {
                // Return the list of attackable plots for highlighting
                return attackables;
            }
        };
        Consumer<HashMap<Integer, CustomBox>> onPicked = (entities) -> {
            CustomBox first = entities.get(1);
            Plot destinationPlot = resolveToPlot(board, first);
            if (destinationPlot == null)
                return;
            int[] destinationPos = destinationPlot.getIndices();
            if (destinationPos == null)
                return;
            int dr = destinationPos[0];
            int dc = destinationPos[1];
            GamePiece targetPiece = board.getGamePieceAtPos(dr, dc);
            if (!(targetPiece instanceof MonsterGamePiece enemy))
                return;
            if (enemy.getAlignment() == owner.getAlignment())
                return;
            // Execute free attack via centralized helper (does not spend an action)
            AbilityUtils.performAttack(board, owner, enemy, toRow, toCol, dr, dc);
        };
        InteractionManager.requestPick(data, filter, onPicked);
    }

    // Helper to resolve either a Plot or a GamePiece into a Plot on the given board
    private static Plot resolveToPlot(Board board, Object box) {
        if (box instanceof Plot p)
            return p;
        if (box instanceof GamePiece gp) {
            Object posObj = gp.getData(GamePieceData.POSITION);
            if (posObj instanceof Board.Position pos && pos.getBoard() == board) {
                var r = pos.getRow();
                var c = pos.getCol();
                var rp = board.getPlotAtPos(r, c);
                if (rp instanceof Plot pp)
                    return pp;
            }
        }
        return null;
    }
}
