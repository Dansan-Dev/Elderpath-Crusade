package io.github.forest_of_dreams.cards;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.characters.pieces.Wolf;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.cards.Card;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.interfaces.OnClick;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.data_objects.Box;

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
public class WolfCard extends Card {

    private final int z; // z-layer used by the card art; used to render the text at the same layer

    // Immediate summon context
    private final Board board;
    private final int targetRow;
    private final int targetCol;
    private final PieceAlignment alignment;

    // Clickable plumbing
    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    // Title overlay (not registered as a child/clickable; rendered manually when face up)
    private final Text title;

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
     * Fully configured WolfCard that immediately summons a Wolf to (targetRow, targetCol) on click.
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

        // Immediate click behavior: summon a Wolf on the specified board position
        setClickableEffect(
            (HashMap<Integer, CustomBox> e) -> {
                System.out.println("CLICKED");
                if (this.board == null) return;
                this.board.addGamePieceToPos(
                    this.targetRow,
                    this.targetCol,
                    new Wolf(0, 0, this.board.getPLOT_WIDTH(), this.board.getPLOT_HEIGHT(), this.alignment)
                );
                // After resolving the effect, consume the card (remove from hand, add to discard, unregister clicks)
                this.consume();
            },
            ClickableEffectData.getImmediate()
        );
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
        }
    }
}
