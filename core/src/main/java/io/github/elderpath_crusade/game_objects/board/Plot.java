package io.github.elderpath_crusade.game_objects.board;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.abilities.BasicAbility;
import io.github.elderpath_crusade.abilities.impl._base.BaseAttackAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.OncePerTurnAttackAbility;
import io.github.elderpath_crusade.abilities.impl._base.BaseMoveAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.JumpMoveAbility;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.sprites.TextureObject;
import io.github.elderpath_crusade.interfaces.Clickable;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.supers.HigherOrderTexture;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.GraphicUtils;
import io.github.elderpath_crusade.managers.HighlightManager;
import io.github.elderpath_crusade.managers.TurnManager;
import lombok.Getter;

import java.util.Arrays;
import java.util.HashMap;

/**
 * A plot is a single square on a Board
 * Contains decor such as plot, plotDirt, and plotDecorFront and plotDecorBack
 * Handles onClick events
 */
public class Plot extends HigherOrderTexture implements Clickable, TargetFilter {
    private TextureObject plotDecorFront;
    private TextureObject plotDecorBack;
    private TextureObject plot;
    private TextureObject plotDirt;

    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    @Getter
    private int row;
    @Getter
    private int col;

    // Board back-reference for movement logic
    @Getter
    private Board boardRef = null;

    public Board getBoard() { return boardRef; }

    public void setGridPos(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public Plot(int x, int y, int width, int height) {
        plot = new TextureObject(ColorSettings.PLOT_GREEN.getColor(), 0, 0, width, height);
        Color hoverColor = plot.getColor().cpy().lerp(Color.BLACK, 0.5f);
        Color clickColor = plot.getColor().cpy().lerp(Color.WHITE, 0.5f);
        plot.setHoverColor(hoverColor);
        plot.setClickColor(clickColor);
        plotDirt = new TextureObject(ColorSettings.PLOT_DIRT_BROWN.getColor(), 0, -(height/2), width, height/2);
        setBounds(new Box(x, y, plot.getWidth(), plot.getHeight()));
        plotDecorFront = EmptyTexture.get(x, y, getWidth(), getHeight());
        plotDecorBack = EmptyTexture.get(x, y, getWidth(), getHeight());

        plotConstruction(plot, plotDirt);
    }

    public Plot withPlotColor(Color color) {
        plot.setColor(color);
        return this;
    }

    // Board back-reference wiring
    public void setBoard(Board board) { this.boardRef = board; }

    // TargetFilter: validate movement or attack targets when this plot is the active source
    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (boardRef == null || box == null) return false;
        // Resolve target to a Plot: accept either Plot or GamePiece (use its current plot)
        Plot targetPlot = null;
        if (box instanceof Plot p) {
            targetPlot = p;
        } else if (box instanceof GamePiece piece) {
            Object posObj = piece.getData(GamePieceData.POSITION);
            if (posObj instanceof Board.Position pos && pos.getBoard() == boardRef) {
                var r = pos.getRow(); var c = pos.getCol();
                var rp = boardRef.getPlotAtPos(r, c);
                if (rp instanceof Plot pp) targetPlot = pp;
            }
        }
        if (targetPlot == null) return false;

        int[] srcIdx = boardRef.getIndicesOfPlot(this);
        if (srcIdx == null) return false;
        GamePiece gp = boardRef.getGamePieceAtPlot(this);
        if (!(gp instanceof MonsterGamePiece mgp)) return false;
        // Only allow interactions when the piece alignment matches current player's turn
        if (mgp.getAlignment() != TurnManager.getCurrentPlayer()) return false;
        // Stunned pieces cannot act (even if they have remaining actions)
        if (mgp.isStunned()) return false;

        // Use BasicAbility validation instead of old getReachablePlots logic
        // Prioritize JumpMoveAbility over BaseMoveAbility if both exist
        JumpMoveAbility jumpMoveAbility = null;
        BaseMoveAbility baseMoveAbility = null;
        BaseAttackAbility baseAttackAbility = null;
        OncePerTurnAttackAbility oncePerTurnAttackAbility = null;

        for (Ability ability : mgp.getAbilities()) {
            if (ability instanceof BasicAbility basicAbility) {
                if (basicAbility instanceof JumpMoveAbility) {
                    jumpMoveAbility = (JumpMoveAbility) basicAbility;
                } else if (basicAbility instanceof BaseMoveAbility) {
                    baseMoveAbility = (BaseMoveAbility) basicAbility;
                } else if (basicAbility instanceof BaseAttackAbility) {
                    baseAttackAbility = (BaseAttackAbility) basicAbility;
                } else if (basicAbility instanceof OncePerTurnAttackAbility) {
                    oncePerTurnAttackAbility = (OncePerTurnAttackAbility) basicAbility;
                }
            }
        }

        // Check if target is an enemy -> try attack ability first
        int[] dstIdx = boardRef.getIndicesOfPlot(targetPlot);
        if (dstIdx == null) return false;
        int dr = dstIdx[0], dc = dstIdx[1];
        GamePiece dstPiece = boardRef.getGamePieceAtPos(dr, dc);

        if (dstPiece instanceof MonsterGamePiece enemy && enemy.getAlignment() != mgp.getAlignment()) {
            // Enemy piece: check attack ability (prioritize OncePerTurnAttackAbility if exists)
            if (oncePerTurnAttackAbility != null) {
                return oncePerTurnAttackAbility.isValidTargetForEffect(targetPlot, targetIndex);
            } else if (baseAttackAbility != null) {
                return baseAttackAbility.isValidTargetForEffect(targetPlot, targetIndex);
            }
            return false;
        }

        // Empty plot: check move ability (prioritize JumpMoveAbility)
        if (jumpMoveAbility != null) {
            return jumpMoveAbility.isValidTargetForEffect(targetPlot, targetIndex);
        } else if (baseMoveAbility != null) {
            return baseMoveAbility.isValidTargetForEffect(targetPlot, targetIndex);
        }

        return false;
    }


    private void plotConstruction(TextureObject plot, TextureObject plotDirt) {
        int width = getWidth();
        int height = getHeight();
        int x = getX();
        int y = getY();

        plotDirt.setZ(-1);
        plot.setZ(0);
        // Back decor shares z=2; front decor z=3
        plotDecorBack.setZ(2);
        plotDecorFront.setZ(3);

        this.plot = plot;
        this.plotDirt = plotDirt;
        setRenderables(Arrays.asList(plotDecorFront, plotDecorBack, plot, plotDirt));

        Box parentBox = new Box(x, y, width, height);
        plot.setParent(parentBox);
        plotDirt.setParent(parentBox);
        plotDecorFront.setParent(parentBox);
        plotDecorBack.setParent(new Box(x, y + height/2, width, height*2));
    }


    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Dynamically decide if this plot should start a movement/attack interaction.
        // Only start if there is a friendly MonsterGamePiece with actions remaining on this plot.
        if (boardRef == null) return null;
        GamePiece gp = boardRef.getGamePieceAtPlot(this);
        if (!(gp instanceof MonsterGamePiece mgp)) return null;
        if (mgp.getAlignment() != TurnManager.getCurrentPlayer()) return null;
        // Stunned pieces cannot act - don't allow plot interaction
        if (mgp.isStunned()) return null;
        // Require at least one action available
        int actionsLeft = mgp.getStats().getRemainingActions();
        if (actionsLeft <= 0) return null;
        return clickableEffectData; // multi-interaction set by Board
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        onClick.run(interactionEntities);
    }
}
