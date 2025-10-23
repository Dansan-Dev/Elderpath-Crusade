package io.github.forest_of_dreams.game_objects.board;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.game_objects.sprites.TextureObject;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.interfaces.OnClick;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.utils.ColorSettings;
import io.github.forest_of_dreams.utils.GraphicUtils;
import lombok.Getter;

import java.util.Arrays;
import java.util.HashMap;

/**
 * A plot is a single square on a Board
 * Contains decor such as plot, plotDirt, and plotDecorFront and plotDecorBack
 * Handles onClick events
 */
public class Plot extends HigherOrderTexture implements Clickable, io.github.forest_of_dreams.interfaces.TargetFilter {
    private TextureObject plotDecorFront;
    private TextureObject plotDecorBack;
    private TextureObject plot;
    private TextureObject plotDirt;

    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    // Board back-reference for movement logic
    private Board boardRef = null;

    // Highlighting state
    @Getter
    private boolean highlighted = false;
    private final EmergingBorderTexture highlightBorder;
    private final CandidateDotTexture candidateDot;

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
        // Emerging highlight border (animated)
        highlightBorder = new EmergingBorderTexture(0, 0, width, height);
        highlightBorder.setZ(1);
        // Candidate move spot indicator
        candidateDot = new CandidateDotTexture(0, 0, width, height);
        candidateDot.setZ(2);
        plotConstruction(plot, plotDirt, highlightBorder, candidateDot);
    }

    /**
     * Animated border used to highlight a Plot during multi-selection.
     * Border thickness smoothly grows when active and shrinks when inactive.
     */
    private static class EmergingBorderTexture extends TextureObject {
        private boolean active = false;
        private float progress = 0f; // 0..1
        private final float speed = 4f; // seconds to full thickness ~0.25s
        private final int maxThickness;
        private final Color borderColor = Color.WHITE;

        EmergingBorderTexture(int x, int y, int width, int height) {
            super(new Color(1,1,1,0f), x, y, width, height);
            // Use a small relative max thickness; at least 2px for visibility
            this.maxThickness = Math.max(2, Math.round(Math.min(width, height) * 0.08f));
        }

        void setActive(boolean active) {
            if (active && !this.active) {
                // Restart emergence when turning on
                this.progress = 0f;
            }
            this.active = active;
        }

        private void step(boolean isPaused) {
            if (isPaused) return;
            float dt = Gdx.graphics.getDeltaTime();
            if (active) {
                progress = Math.min(1f, progress + speed * dt);
            } else {
                progress = Math.max(0f, progress - speed * dt);
            }
        }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
            if (zLevel != this.getZ()) return;
            step(isPaused);
            if (progress <= 0f) return;
            int[] pos = calculatePos();
            drawBorder(batch, pos[0], pos[1]);
        }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
            if (zLevel != this.getZ()) return;
            step(isPaused);
            if (progress <= 0f) return;
            int[] base = calculatePos();
            drawBorder(batch, x + base[0], y + base[1]);
        }

        private void drawBorder(SpriteBatch batch, int absX, int absY) {
            int w = getWidth();
            int h = getHeight();
            int t = Math.max(1, Math.round(maxThickness * progress));
            // Top
            batch.draw(GraphicUtils.getPixelTexture(borderColor), absX, absY + h - t, w, t);
            // Bottom
            batch.draw(GraphicUtils.getPixelTexture(borderColor), absX, absY, w, t);
            // Left
            batch.draw(GraphicUtils.getPixelTexture(borderColor), absX, absY, t, h);
            // Right
            batch.draw(GraphicUtils.getPixelTexture(borderColor), absX + w - t, absY, t, h);
        }
    }

    /**
     * Simple centered dot indicating a plot is a valid movement target while selecting.
     */
    private static class CandidateDotTexture extends TextureObject {
        private boolean active = false;
        private final Color dotColor = Color.WHITE;
        private final float sizeFactor = 0.25f; // 25% of the smaller dimension

        CandidateDotTexture(int x, int y, int width, int height) {
            super(new Color(1,1,1,0f), x, y, width, height);
        }

        void setActive(boolean active) { this.active = active; }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
            if (!active || zLevel != this.getZ()) return;
            int[] pos = calculatePos();
            drawDot(batch, pos[0], pos[1]);
        }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
            if (!active || zLevel != this.getZ()) return;
            int[] base = calculatePos();
            drawDot(batch, x + base[0], y + base[1]);
        }

        private void drawDot(SpriteBatch batch, int absX, int absY) {
            int w = getWidth();
            int h = getHeight();
            int s = Math.max(2, Math.round(Math.min(w, h) * sizeFactor));
            int cx = absX + (w - s) / 2;
            int cy = absY + (h - s) / 2;
            batch.draw(GraphicUtils.getPixelTexture(dotColor), cx, cy, s, s);
        }
    }

    public Plot withPlotColor(Color color) {
        plot.setColor(color);
        return this;
    }

    /** Public API for Board to toggle selection highlight */
    public void setHighlighted(boolean highlighted) {
        if (this.highlighted == highlighted) return;
        this.highlighted = highlighted;
        if (highlightBorder != null) highlightBorder.setActive(highlighted);
    }

    // Candidate indicator control
    public void setCandidate(boolean candidate) {
        if (candidateDot != null) candidateDot.setActive(candidate);
    }

    // Board back-reference wiring
    public void setBoard(Board board) { this.boardRef = board; }

    // TargetFilter: validate movement targets when this plot is the active source
    @Override
    public boolean isValidTargetForEffect(CustomBox box) {
        if (boardRef == null) return false;
        if (!(box instanceof Plot target)) return false;
        int[] srcIdx = boardRef.getIndicesOfPlot(this);
        if (srcIdx == null) return false;
        GamePiece gp = boardRef.getGamePieceAtPlot(this);
        if (!(gp instanceof MonsterGamePiece mgp)) return false;
        if (mgp.getAlignment() != io.github.forest_of_dreams.enums.PieceAlignment.ALLIED) return false;
        int speed = mgp.getStats().getSpeed();
        java.util.List<Plot> reachable = boardRef.getReachablePlots(srcIdx[0], srcIdx[1], speed);
        // Fast path: identity check
        for (Plot p : reachable) {
            if (p == target) return true;
        }
        return false;
    }

    private void applyHighlightTint() {
        // no-op; old tinting replaced by animated border
    }

    private void plotConstruction(TextureObject plot, TextureObject plotDirt, EmergingBorderTexture border, CandidateDotTexture dot) {
        int width = getWidth();
        int height = getHeight();
        int x = getX();
        int y = getY();

        plotDirt.setZ(-1);
        plot.setZ(0);
        // Border sits above the base plot but below decor
        if (border != null) border.setZ(1);
        // Back decor and candidate dot share z=2; front decor z=3
        plotDecorBack.setZ(2);
        if (dot != null) dot.setZ(2);
        plotDecorFront.setZ(3);

        this.plot = plot;
        this.plotDirt = plotDirt;
        // Include border and dot in renderables so they participate in rendering
        setRenderables(Arrays.asList(plotDecorFront, plotDecorBack, plot, plotDirt, border, dot));

        Box parentBox = new Box(x, y, width, height);
        plot.setParent(parentBox);
        plotDirt.setParent(parentBox);
        plotDecorFront.setParent(parentBox);
        plotDecorBack.setParent(new Box(x, y + height/2, width, height*2));
        if (border != null) border.setParent(parentBox);
        if (dot != null) dot.setParent(parentBox);
    }

    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Dynamically decide if this plot should start a movement interaction.
        // Only start if there is a friendly MonsterGamePiece on this plot.
        if (boardRef == null) return null;
        GamePiece gp = boardRef.getGamePieceAtPlot(this);
        if (!(gp instanceof MonsterGamePiece mgp)) return null;
        if (mgp.getAlignment() != io.github.forest_of_dreams.enums.PieceAlignment.ALLIED) return null;
        return clickableEffectData; // multi-interaction set by Board
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        onClick.run(interactionEntities);
    }
}
