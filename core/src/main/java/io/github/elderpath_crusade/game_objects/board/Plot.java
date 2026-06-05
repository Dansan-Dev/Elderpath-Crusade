package io.github.elderpath_crusade.game_objects.board;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.sprites.TextureObject;
import io.github.elderpath_crusade.interfaces.Clickable;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.supers.HigherOrderTexture;
import io.github.elderpath_crusade.utils.ColorSettings;
import lombok.Getter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * A plot is a single square on a Board.
 * Contains decor such as plot, plotDirt, and plotDecorFront and plotDecorBack.
 * Handles onClick events.
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

    @Getter
    private Board boardRef = null;

    public Board getBoard() { return boardRef; }

    public void setGridPos(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int[] getIndices() {
        return new int[]{row, col};
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

    public void setBoard(Board board) { this.boardRef = board; }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (boardRef == null || box == null) return false;
        Plot targetPlot = null;
        if (box instanceof Plot p) {
            targetPlot = p;
        }
        if (targetPlot == null) return false;

        Entity entity = boardRef.getEntityAtPlot(this);
        if (entity == null) return false;
        PieceAlignment alignment = EntityUtils.getAlignment(entity);
        if (alignment != GameContext.get().getTurnManager().getCurrentPlayer()) return false;
        if (EntityUtils.isStunned(entity)) return false;

        int[] dstIdx = targetPlot.getIndices();
        if (dstIdx == null) return false;
        int dr = dstIdx[0], dc = dstIdx[1];
        Entity dstEntity = boardRef.getEntityAtPos(dr, dc);

        if (dstEntity != null && EntityUtils.getAlignment(dstEntity) != alignment) {
            // Attack validation: check if target is in attackable plots
            List<Plot> attackable = boardRef.getAttackableEnemyPlots(this.row, this.col, alignment);
            return attackable.contains(targetPlot);
        }

        if (dstEntity == null) {
            // Move validation: check if target is reachable
            int speed = EntityUtils.getSpeed(entity);
            List<Plot> reachable = boardRef.getReachablePlots(this.row, this.col, speed);
            return reachable.contains(targetPlot);
        }

        return false;
    }

    @Override
    public List<Plot> getEligibleTargets(int targetIndex) {
        if (boardRef == null) return List.of();
        Entity entity = boardRef.getEntityAtPlot(this);
        if (entity == null) return List.of();
        PieceAlignment alignment = EntityUtils.getAlignment(entity);
        if (alignment != GameContext.get().getTurnManager().getCurrentPlayer()) return List.of();
        if (EntityUtils.isStunned(entity)) return List.of();

        int speed = EntityUtils.getSpeed(entity);
        List<Plot> reachable = boardRef.getReachablePlots(this.row, this.col, speed);
        List<Plot> attackable = boardRef.getAttackableEnemyPlots(this.row, this.col, alignment);

        List<Plot> combined = new java.util.ArrayList<>(reachable);
        for (Plot p : attackable) {
            if (!combined.contains(p)) combined.add(p);
        }
        return combined;
    }

    private void plotConstruction(TextureObject plot, TextureObject plotDirt) {
        int width = getWidth();
        int height = getHeight();
        int x = getX();
        int y = getY();

        plotDirt.setZ(-1);
        plot.setZ(0);
        plotDecorFront.setZ(2);
        plotDecorBack.setZ(3);

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
        if (boardRef == null) return null;
        Entity entity = boardRef.getEntityAtPlot(this);
        if (entity == null) return null;
        if (EntityUtils.getAlignment(entity) != GameContext.get().getTurnManager().getCurrentPlayer()) return null;
        if (EntityUtils.isStunned(entity)) return null;
        if (EntityUtils.getRemainingActions(entity) <= 0) return null;
        return clickableEffectData;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        onClick.run(interactionEntities);
    }
}
