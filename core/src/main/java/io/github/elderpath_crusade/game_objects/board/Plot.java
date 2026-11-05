package io.github.elderpath_crusade.game_objects.board;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientController;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ecs.EntityUtils;
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

    /** A legal move/attack destination for this plot's piece — straight from LegalActions, the server's own answer. */
    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (boardRef == null || !(box instanceof Plot targetPlot)) return false;
        Entity entity = boardRef.getEntityAtPlot(this);
        if (entity == null) return false;
        LegalActions.PieceActions pieceActions = legalActionsForPieceAt(entity);
        if (pieceActions == null) return false;
        Coord dest = new Coord(targetPlot.getRow(), targetPlot.getCol());
        return pieceActions.moves().contains(dest) || pieceActions.attacks().contains(dest);
    }

    @Override
    public List<Plot> getEligibleTargets(int targetIndex) {
        if (boardRef == null) return List.of();
        Entity entity = boardRef.getEntityAtPlot(this);
        if (entity == null) return List.of();
        LegalActions.PieceActions pieceActions = legalActionsForPieceAt(entity);
        if (pieceActions == null) return List.of();

        List<Plot> combined = new java.util.ArrayList<>();
        for (Coord c : pieceActions.moves()) addPlotAt(combined, c);
        for (Coord c : pieceActions.attacks()) addPlotAt(combined, c);
        return combined;
    }

    private void addPlotAt(List<Plot> list, Coord c) {
        if (boardRef.getPlotAtPos(c.row(), c.col()) instanceof Plot p && !list.contains(p)) list.add(p);
    }

    /** This piece's LegalActions entry — null if it's not this seat's turn, the piece can't act,
     * or a pick is pending (LegalActions.pieces() is empty then); the server decides all of it. */
    private LegalActions.PieceActions legalActionsForPieceAt(Entity entity) {
        ClientController controller = GameContext.get().getClientController();
        LegalActions actions = controller != null ? controller.legalActions() : null;
        return actions != null ? actions.forPiece(EntityUtils.getId(entity)) : null;
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
        LegalActions.PieceActions pieceActions = legalActionsForPieceAt(entity);
        if (pieceActions == null) return null;
        if (pieceActions.moves().isEmpty() && pieceActions.attacks().isEmpty()) return null;
        return clickableEffectData;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        onClick.run(interactionEntities);
    }
}
