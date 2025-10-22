package io.github.forest_of_dreams.cards;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.characters.pieces.Wolf;
import io.github.forest_of_dreams.enums.ClickableTargetType;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.Plot;
import io.github.forest_of_dreams.game_objects.cards.Card;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.interfaces.TargetFilter;
import io.github.forest_of_dreams.managers.InteractionManager;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.utils.GraphicUtils;
import io.github.forest_of_dreams.interfaces.OnClick;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.utils.Logger;

import java.util.HashMap;
import java.util.List;

/**
 * A concrete Card that represents a Wolf.
 * - When face up, it overlays the text "Wolf" on the card art.
 * - On click (immediate interaction), it summons a Wolf onto a specific plot on the provided Board.
 *
 * Note: Multiclick target selection is not yet implemented in the project, so this version
 * summons immediately to a fixed target (row/col) supplied at construction time.
 */
public class WolfCard extends Card implements TargetFilter {

    private final int z; // z-layer used by the card art; used to render the text at the same layer

    // Context
    private final Board board;
    private final int targetRow;
    private final int targetCol;
    private final PieceAlignment alignment;

    // Clickable plumbing
    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    // Title overlay (not registered as a child/clickable; rendered manually when face up)
    private final Text title;

    // Source selection highlight (animated emerging border)
    private float borderProgress = 0f; // 0..1
    private final float borderSpeed = 4f; // ~0.25s to fully show/hide

    /**
     * Minimal constructor that only renders a Wolf card front/back and title. No click behavior.
     * Useful if you intend to call card.play(board, row, col) externally.
     */
    public WolfCard(int x, int y, int width, int height, int z) {
        super(x, y, width, height, z, null);
        this.z = z;
        this.board = null;
        this.targetRow = -1;
        this.targetCol = -1;
        this.alignment = PieceAlignment.ALLIED;
        this.title = makeTitle();
    }

    /**
     * Fully configured WolfCard that uses multi-selection: click the card, then click a Plot to summon there.
     * The targetRow/targetCol params are retained for backward compatibility but ignored by the multi flow.
     */
    public WolfCard(Board board, int targetRow, int targetCol, PieceAlignment alignment,
                    int x, int y, int width, int height, int z) {
        super(x, y, width, height, z, null);
        this.z = z;
        this.board = board;
        this.targetRow = targetRow;
        this.targetCol = targetCol;
        this.alignment = alignment;
        this.title = makeTitle();

        // Multi-selection: select exactly one Plot; on resolution, summon Wolf there and consume the card.
        setClickableEffect(
            (HashMap<Integer, CustomBox> entities) -> {
                if (this.board == null) return;
                Object t = entities.get(1);
                if (!(t instanceof Plot plot)) return;
                int[] idx = this.board.getIndicesOfPlot(plot);
                if (idx == null) return;
                // Safety: ensure target plot is still empty at resolution time
                if (this.board.getGamePieceAtPos(idx[0], idx[1]) != null) {
                    Logger.log("WolfCard", "Summon aborted: target plot is occupied at (" + idx[0] + "," + idx[1] + ")");
                    return;
                }
                this.board.addGamePieceToPos(
                    idx[0],
                    idx[1],
                    new Wolf(0, 0, this.board.getPLOT_WIDTH(), this.board.getPLOT_HEIGHT(), this.alignment)
                );
                this.consume();
            },
            ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1)
        );
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box) {
        if (board == null) return false;
        if (!(box instanceof Plot plot)) return false;
        int[] idx = board.getIndicesOfPlot(plot);
        if (idx == null) return false;
        int row = idx[0];
        // Must be an empty plot
        if (board.getGamePieceAtPlot(plot) != null) return false;
        switch (alignment) {
            case ALLIED:
                return row == 0; // only first row for allied player
            case HOSTILE:
                // Future-proof: restrict to last row for hostile
                return row == board.getROWS() - 1;
            default:
                return false;
        }
    }

    private Text makeTitle() {
        Text t = new Text("Wolf", FontType.SILKSCREEN, 0, 0, z, Color.WHITE);
        // Reasonable default font size relative to card height
        t.withFontSize(Math.max(12, (int)(getHeight() * 0.15f)));
        return t;
    }

    // Keep the title sizing roughly in sync when card bounds change
    @Override
    public void setBounds(Box bounds) {
        super.setBounds(bounds);
        if (title != null) {
            title.withFontSize(Math.max(12, (int)(getHeight() * 0.15f)));
        }
    }

    // Clickable wiring
    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        return clickableEffectData;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        this.onClick.run(interactionEntities);
    }

    // Rendering: draw base card art via parent, then overlay title when face up
    @Override
    public List<Integer> getZs() {
        // Delegate to Card for z-levels derived from the active side
        return super.getZs();
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        super.render(batch, zLevel, isPaused);
        if (isPaused) return;
        if (isFaceDown()) return;
        // Center the title within the card's local bounds
        if (zLevel == z) {
            int[] abs = calculatePos();
            int cardX = abs[0];
            int cardY = abs[1];
            int tx = cardX + (getWidth() - title.getWidth()) / 2;
            int ty = cardY + (int)(getHeight() * 0.75f) - title.getHeight() / 2; // upper quadrant
            title.render(batch, zLevel, false, tx, ty);

            // Draw animated border if this card is the active selection source
            boolean active = InteractionManager.hasActiveSelection() && InteractionManager.getActiveSource() == this;
            float dt = Gdx.graphics.getDeltaTime();
            if (active) borderProgress = Math.min(1f, borderProgress + borderSpeed * dt);
            else borderProgress = Math.max(0f, borderProgress - borderSpeed * dt);
            if (borderProgress > 0f) {
                int w = getWidth();
                int h = getHeight();
                int t = Math.max(2, Math.round(Math.min(w, h) * 0.08f * borderProgress));
                // Top
                batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), cardX, cardY + h - t, w, t);
                // Bottom
                batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), cardX, cardY, w, t);
                // Left
                batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), cardX, cardY, t, h);
                // Right
                batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), cardX + w - t, cardY, t, h);
            }
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        super.render(batch, zLevel, isPaused, x, y);
        if (isPaused) return;
        if (isFaceDown()) return;
        if (zLevel == z) {
            int tx = x + (getWidth() - title.getWidth()) / 2;
            int ty = y + (int)(getHeight() * 0.75f) - title.getHeight() / 2;
            title.render(batch, zLevel, false, tx, ty);

            // Animated border (same as other render overload) using absolute position
            boolean active = InteractionManager.hasActiveSelection() && InteractionManager.getActiveSource() == this;
            float dt = Gdx.graphics.getDeltaTime();
            if (active) borderProgress = Math.min(1f, borderProgress + borderSpeed * dt);
            else borderProgress = Math.max(0f, borderProgress - borderSpeed * dt);
            if (borderProgress > 0f) {
                // In the (x,y) render overload, the active side is rendered at (x,y),
                // so draw the border at the same absolute coordinates to align perfectly.
                int absX = x;
                int absY = y;
                int w = getWidth();
                int h = getHeight();
                int t2 = Math.max(2, Math.round(Math.min(w, h) * 0.08f * borderProgress));
                batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), absX, absY + h - t2, w, t2);
                batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), absX, absY, w, t2);
                batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), absX, absY, t2, h);
                batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), absX + w - t2, absY, t2, h);
            }
        }
    }
}
