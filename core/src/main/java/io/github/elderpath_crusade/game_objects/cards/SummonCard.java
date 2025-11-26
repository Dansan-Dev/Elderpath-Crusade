package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.managers.PlayerManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.managers.GameModeManager;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.Logger;
import io.github.elderpath_crusade.utils.ColorSettings;
import com.badlogic.gdx.utils.Align;
import io.github.elderpath_crusade.utils.CardRenderUtils;
import java.util.Map;

import java.util.HashMap;
import java.util.List;

/**
 * Base class for summon-type cards. Handles multi-target click flow, mana cost,
 * summoning onto a Board plot, emitting events, and consuming the card.
 * Subclasses provide the concrete piece instantiation and stats/name.
 */
public abstract class SummonCard extends Card implements TargetFilter {
    protected final Board board;
    protected final PieceAlignment alignment;
    protected final GamePieceStats stats; // unified stats used by both card and resulting piece

    // Cached stat texts (rendered inside orbs)
    private Text manaText; // top-right big orb
    private Text hpText; // bottom-left big orb (health = maxHealth)
    private Text spdText; // bottom small (second-left)
    private Text actText; // bottom small (second-right)
    private Text atkText; // bottom-right big orb
    // Card rules text area (optional)
    private Text descText; // centered rules text under title

    // Clickable plumbing for InteractionManager
    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    protected SummonCard(
        Board board, PieceAlignment alignment,
        int x, int y,
        int width, int height,
        int z
    ) {
        super(x, y, width, height, z, null);
        this.board = board;
        this.alignment = alignment;
        this.stats = buildStats();
        setTitle(getCardName(), FontType.SILKSCREEN);
        setTitleColor(Color.WHITE);
        // Initialize stat text overlays
        initStatTexts();
        // Initialize description text from card-provided ability descriptions (joined with two newlines)
        List<String> descs = getAbilityDescriptionsForCard();
        if (descs != null && !descs.isEmpty()) {
            String desc = String.join("\n\n", descs);
            descText = new Text(desc, FontType.SILKSCREEN, 0, 0, getZLayer(), ColorSettings.TEXT_DEFAULT.getColor());
            // Initial wrap will be applied in setBounds and during render
        }
        initializeClickableEffect();
    }

    // Subclass hooks
    protected abstract GamePieceStats buildStats();
    protected abstract String getCardName();
    protected abstract GamePiece instantiatePiece(GamePieceStats stats);
    // Ability descriptions to show on the card (provided by the card itself)
    protected abstract List<String> getAbilityDescriptionsForCard();

    // --- Stat overlay initialization and sizing ---
    private void initStatTexts() {
        Color c = Color.WHITE;
        int z = getZLayer();
        manaText = new Text(String.valueOf(stats.getCost()), FontType.SILKSCREEN, 0, 0, z, c);
        hpText = new Text(String.valueOf(stats.getMaxHealth()), FontType.SILKSCREEN, 0, 0, z, c);
        spdText = new Text(String.valueOf(stats.getSpeed()), FontType.SILKSCREEN, 0, 0, z, c);
        actText = new Text(String.valueOf(stats.getActions()), FontType.SILKSCREEN, 0, 0, z, c);
        atkText = new Text(String.valueOf(stats.getDamage()), FontType.SILKSCREEN, 0, 0, z, c);
        updateStatTextSizes();
    }

    private void updateStatTextSizes() {
        int h = getBounds().getHeight();
        int big = Math.max(8, (int) (h * 0.08f));
        int small = Math.max(8, (int) (h * 0.06f));
        if (manaText != null) manaText.withFontSize(big);
        if (hpText != null) hpText.withFontSize(big);
        if (atkText != null) atkText.withFontSize(big);
        if (spdText != null) spdText.withFontSize(small);
        if (actText != null) actText.withFontSize(small);
    }

    public int getManaCost() {
        return stats.getCost();
    }

    /**
     * Read-only access to the base stats represented by this card.
     * Returns a copy to prevent external mutation of the card's internal state.
     */
    public GamePieceStats getStats() {
        return stats.copy();
    }

    @Override
    public void setBounds(Box bounds) {
        super.setBounds(bounds);
        updateStatTextSizes();
        // Update description wrap bounds on resize
        if (descText != null) {
            int w = getBounds().getWidth();
            int h = getBounds().getHeight();
            int marginX = Math.round(w * CardRenderUtils.DESC_MARGIN_X_PCT);
            int wrapW = Math.max(1, w - marginX * 2);
            int wrapH = Math.max(1, Math.round(h * CardRenderUtils.DESC_HEIGHT_PCT));
            descText.withWrapBounds(wrapW, wrapH).withAlignment(Align.center);
        }
    }

    private void initializeClickableEffect() {
        setClickableEffect(
            (HashMap<Integer, CustomBox> entities) -> {
                if (board == null) return;
                Object secondClicked = entities.get(1);
                if (!(secondClicked instanceof Plot plot)) return;
                int[] plotPos = board.getIndicesOfPlot(plot);
                if (plotPos == null) return;

                // Reject if occupied
                if (board.getGamePieceAtPos(plotPos[0], plotPos[1]) != null) {
                    Logger.log("SummonCard", "Summon aborted: occupied (" + plotPos[0] + "," + plotPos[1] + ")");
                    return;
                }

                // Mana check and spend using unified stats cost
                PlayerManager.PlayerState playerState = PlayerManager.get(alignment);
                int cost = stats.getCost();
                if (playerState == null || playerState.mana < cost) {
                    Logger.log("SummonCard", "Not enough mana. Need=" + cost + ", have=" + (playerState == null ? 0 : playerState.mana));
                    return;
                }
                playerState.mana -= cost;

                GamePiece piece = instantiatePiece(stats.copy());
                if (piece == null) {
                    Logger.error("SummonCard", "instantiatePiece(stats) returned null for " + getCardName() + "Card");
                    return;
                }
                board.addGamePieceToPos(plotPos[0], plotPos[1], piece);

                // Emit generic CARD_PLAYED
                EventBus.emit(
                    GameEventType.CARD_PLAYED,
                    Map.of(
                        "card", getCardName(),
                        "owner", alignment.name(),
                        "row", plotPos[0],
                        "col", plotPos[1],
                        "pieceId", piece.getId().toString()
                    )
                );

                // Move the card from hand to discard
                consume();
            },
            ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1)
        );
    }

    // Render stat texts inside orbs (face-up only; Card controls gating and z-level)
    @Override
    protected void renderExtraOverlays(SpriteBatch batch, int zLevel, boolean isPaused, int baseX, int baseY) {
        CardRenderUtils.renderUnitCardOverlays(
                batch, zLevel, isPaused,
                baseX, baseY, getWidth(), getHeight(),
                manaText, hpText, spdText, actText, atkText,
                descText);
    }

    // TargetFilter for InteractionManager validation
    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (board == null) return false;
        if (!(box instanceof Plot plot)) return false;
        return board.isValidSummonTarget(plot, alignment);
    }

    // Clickable integration — stored here because Card is generic
    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        this.onClick.run(interactionEntities);
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Only allow playing on owner turn.
        // If P2 bot is enabled AND not in LOCAL_MATCH mode, block human clicks on P2 cards.
        if (
            alignment == PieceAlignment.P2
            && SettingsManager.debug.enableP2Bot
            && GameModeManager.getCurrent() != GameMode.LOCAL_MATCH
        ) {
            return null;
        }

        // Only allow playing if it's the current player's turn
        if (alignment != TurnManager.getCurrentPlayer()) {
            return null;
        }

        // Check if player has enough mana to play this card
        PlayerManager.PlayerState playerState = PlayerManager.get(alignment);
        int cost = stats.getCost();
        if (playerState == null || playerState.mana < cost) {
            return null; // Not enough mana - card is not clickable
        }

        return clickableEffectData;
    }
}
