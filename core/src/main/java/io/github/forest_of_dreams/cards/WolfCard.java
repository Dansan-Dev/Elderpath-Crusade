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
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.multiplayer.EventBus;
import io.github.forest_of_dreams.multiplayer.GameEventType;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.utils.GraphicUtils;
import io.github.forest_of_dreams.interfaces.OnClick;
import io.github.forest_of_dreams.utils.Logger;
import io.github.forest_of_dreams.managers.TurnManager;
import io.github.forest_of_dreams.managers.PlayerManager;

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

    private static final int COST = 1; // Mana cost to play this card

    private final int z; // z-layer used by the card art; used to render the text at the same layer

    // Context
    private final Board board;
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
     * Fully configured WolfCard that uses multi-selection: click the card, then click a Plot to summon there.
     * The targetRow/targetCol params are retained for backward compatibility but ignored by the multi flow.
     */
    public WolfCard(Board board, PieceAlignment alignment,
                    int x, int y, int width, int height, int z) {
        super(x, y, width, height, z, null);
        this.z = z;
        this.board = board;
        this.alignment = alignment;
        this.title = makeTitle();

        initializeClickableEffect();
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box) {
        if (board == null) return false;
        if (!(box instanceof Plot plot)) return false;
        return board.isValidSummonTarget(plot, alignment);
    }

    private Text makeTitle() {
        return new Text("Wolf", FontType.SILKSCREEN, 0, 0, z, Color.WHITE)
            .withFontSize(Math.max(12, (int)(getHeight() * 0.15f)));
    }

    // Keep the title sizing roughly in sync when card bounds change
    @Override
    public void setBounds(Box bounds) {
        super.setBounds(bounds);
        if (title != null) {
            title.withFontSize(Math.max(12, (int)(getHeight() * 0.15f)));
        }
    }

    public void initializeClickableEffect() {
        // Multi-selection: select exactly one Plot; on resolution, summon Wolf there and consume the card.
        setClickableEffect(
            (HashMap<Integer, CustomBox> entities) -> {
                if (this.board == null) return;
                Object secondClicked = entities.get(1);
                if (!(secondClicked instanceof Plot plot)) return;
                int[] plotPos = this.board.getIndicesOfPlot(plot);
                if (plotPos == null) return;

                // Check for game piece
                if (this.board.getGamePieceAtPos(plotPos[0], plotPos[1]) != null) {
                    Logger.log("WolfCard", "Summon aborted: target plot is occupied at (" + plotPos[0] + "," + plotPos[1] + ")");
                    return;
                }

                // Check available mana and spend it
                PlayerManager.PlayerState playerState = PlayerManager.get(this.alignment);
                if (playerState == null || playerState.mana < COST) {
                    Logger.log("WolfCard", "Not enough mana to play WolfCard. Required=" + COST + ", have=" + (playerState == null ? 0 : playerState.mana));
                    // Do not emit a gameplay event for failed plays; no state change occurs
                    return;
                }
                playerState.mana -= COST;
                Wolf wolf = new Wolf(0, 0, this.board.getPLOT_WIDTH(), this.board.getPLOT_HEIGHT(), this.alignment);
                this.board.addGamePieceToPos(
                    plotPos[0],
                    plotPos[1],
                    wolf
                );

                // Emit CARD_PLAYED event
                EventBus.emit(
                    GameEventType.CARD_PLAYED,
                    java.util.Map.of(
                        "card", "WolfCard",
                        "owner", this.alignment.name(),
                        "row", plotPos[0],
                        "col", plotPos[1],
                        "pieceId", wolf.getId().toString()
                    )
                );

                // Consume the card
                this.consume();
            },
            ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1)
        );
    }

    // Clickable wiring
    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Only allow playing this card on the owning player's turn.
        // Additionally, when P2 bot is enabled, block human clicks on P2's cards (the bot will trigger directly).
        if (
            alignment == PieceAlignment.P2 &&
            SettingsManager.debug.enableP2Bot
        ) {
            return null;
        }
        return (alignment == TurnManager.getCurrentPlayer())
            ? clickableEffectData
            : null;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        this.onClick.run(interactionEntities);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        super.render(batch, zLevel, isPaused);
        if (isPaused) return;
        if (isFaceDown()) return;
        if (zLevel == z) {
            int[] abs = calculatePos();
            int cardX = abs[0];
            int cardY = abs[1];
            renderTitle(batch, zLevel, cardX, cardY);
            animateBorder(batch, zLevel, cardX, cardY);
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        super.render(batch, zLevel, isPaused, x, y);
        if (isPaused) return;
        if (isFaceDown()) return;
        if (zLevel == z) {
            renderTitle(batch, zLevel, x, y);
            animateBorder(batch, zLevel, x, y);
        }
    }

    private void renderTitle(SpriteBatch batch, int zLevel, int x, int y) {
        int titleX = x + (getWidth() - title.getWidth()) / 2;
        int titleY = y + (int)(getHeight() * 0.75f) - title.getHeight() / 2;
        title.render(batch, zLevel, false, titleX, titleY);
    }

    private void animateBorder(SpriteBatch batch, int zLevel, int x, int y) {
        boolean active = InteractionManager.hasActiveSelection() && InteractionManager.getActiveSource() == this;
        float dt = Gdx.graphics.getDeltaTime();
        if (active) borderProgress = Math.min(1f, borderProgress + borderSpeed * dt);
        else borderProgress = Math.max(0f, borderProgress - borderSpeed * dt);

        if (borderProgress > 0f) {
            int width = getWidth();
            int height = getHeight();
            int thickness = Math.max(2, Math.round(Math.min(width, height) * 0.08f * borderProgress));
            // Top
            batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), x, y + height - thickness, width, thickness);
            // Bottom
            batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), x, y, width, thickness);
            // Left
            batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), x, y, thickness, height);
            // Right
            batch.draw(GraphicUtils.getPixelTexture(Color.WHITE), x + width - thickness, y, thickness, height);
        }
    }
}
