package io.github.elderpath_crusade.abilities.impl._multi.other;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.ActionableAbility;
import io.github.elderpath_crusade.utils.AbilityUtils;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.managers.TurnManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * StormMage actionable ability: "Storm"
 * Range 2 (Chebyshev distance): select a plot as center, deal 2 damage to center and 1 damage to all 8 surrounding squares.
 * Costs 1 action and can only be used once per turn.
 */
public class StormActionAbility implements ActionableAbility, TriggeredAbility, TargetFilter {
    private final MonsterGamePiece owner;
    private boolean usedThisTurn = false;

    public StormActionAbility(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public String getName() { return "Storm"; }

    @Override
    public String getDescription() { return StormActionAbility.getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "STORM ACTION (1/turn): Pick a center within 2 range. Deal 2 damage in center and 1 damage to surrounding squares";
    }

    @Override
    public AbilityType getType() { return AbilityType.ACTIONABLE; }

    @Override
    public String getIconPath() { return null; }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Single pick: the center plot
        return ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1);
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (owner == null) return false;
        if (usedThisTurn) return false;
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return false;
        if (!AbilityUtils.canAct(owner)) return false;

        // Resolve to Plot
        Plot plot = resolveToPlot(box);
        if (plot == null) return false;

        // Get owner's position
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos)) return false;
        Board board = ownerPos.getBoard();
        if (board == null) return false;

        // Get plot's position
        int[] plotIndices = plot.getIndices();
        if (plotIndices == null) return false;
        int plotRow = plotIndices[0];
        int plotCol = plotIndices[1];

        // Check if within Chebyshev distance 2
        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();
        int rowDiff = Math.abs(plotRow - ownerRow);
        int colDiff = Math.abs(plotCol - ownerCol);
        int chebyshevDistance = Math.max(rowDiff, colDiff);
        return chebyshevDistance <= 2;
    }

    @Override
    public List<Plot> getEligibleTargets(int targetIndex) {
        if (owner == null) return List.of();
        if (usedThisTurn) return List.of();
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return List.of();
        if (!AbilityUtils.canAct(owner)) return List.of();

        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos)) return List.of();
        Board board = ownerPos.getBoard();
        if (board == null) return List.of();

        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();
        List<Plot> eligible = new ArrayList<>();

        // Find all plots within Chebyshev distance 2
        for (int r = 0; r < board.getROWS(); r++) {
            for (int c = 0; c < board.getCOLS(); c++) {
                int rowDiff = Math.abs(r - ownerRow);
                int colDiff = Math.abs(c - ownerCol);
                int chebyshevDistance = Math.max(rowDiff, colDiff);
                if (chebyshevDistance <= 2) {
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
        if (usedThisTurn) return;
        if (TurnManager.getCurrentPlayer() != owner.getAlignment()) return;
        if (!AbilityUtils.canAct(owner)) return;

        // Get center plot
        CustomBox firstClicked = entities.get(1);
        Plot centerPlot = resolveToPlot(firstClicked);
        if (centerPlot == null) return;

        // Get owner's position and board
        Object ownerPosObj = owner.getData(GamePieceData.POSITION);
        if (!(ownerPosObj instanceof Board.Position ownerPos)) return;
        Board board = ownerPos.getBoard();
        if (board == null) return;

        // Get center plot's position
        int[] centerIndices = centerPlot.getIndices();
        if (centerIndices == null) return;
        int centerRow = centerIndices[0];
        int centerCol = centerIndices[1];

        // Verify range
        int ownerRow = ownerPos.getRow();
        int ownerCol = ownerPos.getCol();
        int rowDiff = Math.abs(centerRow - ownerRow);
        int colDiff = Math.abs(centerCol - ownerCol);
        int chebyshevDistance = Math.max(rowDiff, colDiff);
        if (chebyshevDistance > 2) return;

        // Deal 2 damage to center
        GamePiece centerPiece = board.getGamePieceAtPos(centerRow, centerCol);
        if (centerPiece instanceof MonsterGamePiece centerTarget) {
            AbilityUtils.dealDamage(centerTarget, 2, owner, true);
            try {
                centerTarget.notifyDamaged(2, owner);
            } catch (Exception ignored) {}
        }

        // Deal 1 damage to all 8 surrounding squares (cardinal + diagonal)
        int[][] surroundingDirs = new int[][]{
            {-1, -1}, {-1, 0}, {-1, 1},  // Top row
            {0, -1},           {0, 1},   // Middle row (center is already done)
            {1, -1},  {1, 0},  {1, 1}    // Bottom row
        };

        for (int[] dir : surroundingDirs) {
            int r = centerRow + dir[0];
            int c = centerCol + dir[1];
            if (r < 0 || r >= board.getROWS() || c < 0 || c >= board.getCOLS()) continue;

            GamePiece piece = board.getGamePieceAtPos(r, c);
            if (piece instanceof MonsterGamePiece target) {
                AbilityUtils.dealDamage(target, 1, owner, true);
                try {
                    target.notifyDamaged(1, owner);
                } catch (Exception ignored) {}
            }
        }

        // Mark as used this turn
        usedThisTurn = true;

        // Spend 1 action from owner
        AbilityUtils.spendAction(owner);
    }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        // No-op, owner is set in constructor
    }

    @Override
    public void onDetach() {
        // No-op
    }

    @Override
    public void onTurnEnded(PieceAlignment endingPlayer) {
        // Reset usage when owner's turn ends
        if (owner != null && endingPlayer == owner.getAlignment()) {
            usedThisTurn = false;
        }
    }

    private Plot resolveToPlot(CustomBox box) {
        if (box instanceof Plot plot) return plot;
        if (box instanceof GamePiece gp) {
            Object posObj = gp.getData(GamePieceData.POSITION);
            if (posObj instanceof Board.Position pos) {
                Board board = pos.getBoard();
                if (board != null) {
                    Renderable renderable = board.getPlotAtPos(pos.getRow(), pos.getCol());
                    if (renderable instanceof Plot p) return p;
                }
            }
        }
        return null;
    }
}

