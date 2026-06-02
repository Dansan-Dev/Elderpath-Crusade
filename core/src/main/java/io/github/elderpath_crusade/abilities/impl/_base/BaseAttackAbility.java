package io.github.elderpath_crusade.abilities.impl._base;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.BasicAbility;
import io.github.elderpath_crusade.utils.AbilityUtils;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.game.TurnManager;

import java.util.HashMap;
import java.util.List;

/**
 * Base attack ability that all pieces have by default.
 * Allows attacking enemy units within range.
 * This is a BasicAbility - automatically available when piece is selected, not
 * shown in ability popup.
 */
public class BaseAttackAbility implements BasicAbility {
    private final MonsterGamePiece owner;

    public BaseAttackAbility(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public String getName() {
        return "Attack";
    }

    @Override
    public String getDescription() {
        return "Attack an enemy within range";
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Single target: enemy piece (via plot)
        return ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1);
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (owner == null)
            return false;
        if (GameContext.get().getTurnManager().getCurrentPlayer() != owner.getAlignment())
            return false;
        if (!AbilityUtils.canAct(owner))
            return false;

        // Resolve to Plot
        Plot plot = resolveToPlot(box);
        if (plot == null)
            return false;

        // Get owner's position
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos))
            return false;
        Board board = ownerPos.getBoard();
        if (board == null)
            return false;

        // Get plot's position
        if (plot.getIndices() == null)
            return false;

        // Check if there's an attackable enemy at this plot
        List<Plot> attackables = board.getAttackableEnemyPlots(ownerPos.getRow(), ownerPos.getCol(),
                owner.getAlignment());
        for (Plot p : attackables) {
            if (p == plot)
                return true;
        }
        return false;
    }

    @Override
    public List<Plot> getEligibleTargets(int targetIndex) {
        if (owner == null)
            return List.of();
        if (GameContext.get().getTurnManager().getCurrentPlayer() != owner.getAlignment())
            return List.of();
        if (AbilityUtils.getRemainingActions(owner) <= 0)
            return List.of();

        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos))
            return List.of();
        Board board = ownerPos.getBoard();
        if (board == null)
            return List.of();

        return board.getAttackableEnemyPlots(
                ownerPos.getRow(),
                ownerPos.getCol(),
                owner.getAlignment());
    }

    @Override
    public void execute(HashMap<Integer, CustomBox> entities) {
        if (owner == null)
            return;
        if (GameContext.get().getTurnManager().getCurrentPlayer() != owner.getAlignment())
            return;
        if (AbilityUtils.getRemainingActions(owner) <= 0)
            return;

        // Get target plot
        CustomBox firstClicked = entities.get(1);
        Plot targetPlot = resolveToPlot(firstClicked);
        if (targetPlot == null)
            return;

        // Get owner's position and board
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos))
            return;
        Board board = ownerPos.getBoard();
        if (board == null)
            return;

        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();

        // Get target plot's position
        int[] targetIndices = targetPlot.getIndices();
        if (targetIndices == null)
            return;
        int targetRow = targetIndices[0];
        int targetCol = targetIndices[1];

        // Get target piece
        GamePiece targetPiece = board.getGamePieceAtPos(targetRow, targetCol);
        if (!(targetPiece instanceof MonsterGamePiece enemy))
            return;
        if (enemy.getAlignment() == owner.getAlignment())
            return; // Must be enemy

        // Validate that target is attackable
        List<Plot> attackables = board.getAttackableEnemyPlots(ownerRow, ownerCol, owner.getAlignment());
        boolean valid = false;
        for (Plot p : attackables) {
            if (p == targetPlot) {
                valid = true;
                break;
            }
        }
        if (!valid)
            return;

        // Perform the attack
        AbilityUtils.performAttack(
                board,
                owner,
                enemy,
                ownerRow,
                ownerCol,
                targetRow,
                targetCol);

        // Spend 1 action
        AbilityUtils.spendAction(owner);
    }

    private Plot resolveToPlot(CustomBox box) {
        if (box instanceof Plot plot)
            return plot;
        if (box instanceof GamePiece gp) {
            Object posObj = gp.getData(GamePieceData.POSITION);
            if (posObj instanceof Board.Position pos) {
                Board board = pos.getBoard();
                if (board != null) {
                    Renderable renderable = board.getPlotAtPos(pos.getRow(), pos.getCol());
                    if (renderable instanceof Plot p)
                        return p;
                }
            }
        }
        return null;
    }
}
