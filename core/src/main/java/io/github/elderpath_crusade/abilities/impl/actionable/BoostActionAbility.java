package io.github.elderpath_crusade.abilities.impl.actionable;

import io.github.elderpath_crusade.abilities.ActionableAbility;
import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.utils.AbilityUtils;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.plot.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.managers.BoardManager;
import io.github.elderpath_crusade.managers.TurnManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * WindSpirit actionable ability: "Boost"
 * Select an adjacent friendly unit and give it +1 action this turn.
 * Costs 1 action from the WindSpirit.
 */
public class BoostActionAbility implements ActionableAbility, TargetFilter {
    private final MonsterGamePiece owner;

    public BoostActionAbility(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public String getName() { return "Boost"; }

    @Override
    public String getDescription() { return BoostActionAbility.getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "BOOST ACTION: Give adjacent friendly unit +1 action this turn";
    }

    @Override
    public AbilityType getType() { return AbilityType.ACTIONABLE; }

    @Override
    public String getIconPath() { return null; }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Single pick: adjacent friendly unit (via plot)
        return ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1);
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (owner == null) return false;
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return false;
        if (!AbilityUtils.canAct(owner)) return false;

        // Resolve to MonsterGamePiece
        MonsterGamePiece target = resolveToMonsterGamePiece(box);
        if (target == null) return false;
        if (target == owner) return false; // Cannot boost self

        // Must be friendly
        if (target.getAlignment() != owner.getAlignment()) return false;

        // Must be adjacent (cardinal)
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        Object targetPosObj = target.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos) || !(targetPosObj instanceof Board.Position targetPos)) {
            return false;
        }
        if (ownerPos.getBoard() != targetPos.getBoard()) return false;

        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();
        int targetRow = targetPos.getRow();
        int targetCol = targetPos.getCol();

        int rowDiff = Math.abs(targetRow - ownerRow);
        int colDiff = Math.abs(targetCol - ownerCol);
        return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1); // Cardinal adjacency
    }

    @Override
    public List<Plot> getEligibleTargets(int targetIndex) {
        if (owner == null) return List.of();
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return List.of();
        if (!AbilityUtils.canAct(owner)) return List.of();

        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos)) return List.of();
        Board board = ownerPos.getBoard();
        if (board == null) return List.of();

        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();
        PieceAlignment ownerAlignment = owner.getAlignment();
        List<Plot> eligible = new ArrayList<>();

        // Check cardinal directions for adjacent friendly units
        int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] dir : dirs) {
            int r = ownerRow + dir[0];
            int c = ownerCol + dir[1];
            if (r < 0 || r >= board.getROWS() || c < 0 || c >= board.getCOLS()) continue;

            GamePiece gp = board.getGamePieceAtPos(r, c);
            if (gp instanceof MonsterGamePiece mgp) {
                if (mgp != owner && mgp.getAlignment() == ownerAlignment) {
                    Renderable renderable = board.getPlotAtPos(r, c);
                    if (renderable instanceof Plot plot) {
                        eligible.add(plot);
                    }
                }
            }
        }

        return eligible;
    }

    @Override
    public void execute(HashMap<Integer, CustomBox> entities) {
        if (owner == null) return;
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return;
        if (!AbilityUtils.canAct(owner)) return;

        // Get target
        CustomBox firstClicked = entities.get(1);
        MonsterGamePiece target = resolveToMonsterGamePiece(firstClicked);
        if (target == null) return;
        if (target == owner) return; // Cannot boost self

        // Verify friendly
        if (target.getAlignment() != owner.getAlignment()) return;

        // Verify adjacency
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        Object targetPosObj = target.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos) || !(targetPosObj instanceof Board.Position targetPos)) {
            return;
        }
        if (ownerPos.getBoard() != targetPos.getBoard()) return;

        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();
        int targetRow = targetPos.getRow();
        int targetCol = targetPos.getCol();

        int rowDiff = Math.abs(targetRow - ownerRow);
        int colDiff = Math.abs(targetCol - ownerCol);
        if (!((rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1))) {
            return; // Not adjacent
        }

        // Give +1 action to target
        int currentActions = target.getStats().getRemainingActions();
        target.getStats().setRemainingActions(currentActions + 1);

        // Spend 1 action from owner
        AbilityUtils.spendAction(owner);
    }

    private MonsterGamePiece resolveToMonsterGamePiece(CustomBox box) {
        if (box instanceof MonsterGamePiece mgp) return mgp;
        if (box instanceof Plot plot) {
            Board board = BoardManager.getBoard();
            if (board != null) {
                int[] indices = plot.getIndices();
                if (indices != null) {
                    GamePiece gp = board.getGamePieceAtPos(indices[0], indices[1]);
                    if (gp instanceof MonsterGamePiece mgp) return mgp;
                }
            }
        }
        return null;
    }
}

