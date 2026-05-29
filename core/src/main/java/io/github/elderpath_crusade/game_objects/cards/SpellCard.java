package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.managers.PlayerManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.managers.GameModeManager;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.CardRenderUtils;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.Logger;
import io.github.elderpath_crusade.data_objects.Box;

import java.util.HashMap;

/**
 * Base class for spell-type cards. Handles targeting, mana cost,
 * manual effect execution, and standard spell rendering (Mana + Description).
 */
public abstract class SpellCard extends Card implements TargetFilter {
    protected final Board board;
    protected final PieceAlignment alignment;

    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    private Text manaText;
    private Text descText;

    protected SpellCard(
            Board board, PieceAlignment alignment,
            int x, int y,
            int width, int height,
            int z) {
        super(x, y, width, height, z, null);
        this.board = board;
        this.alignment = alignment;

        setTitle(getSpellName(), FontType.SILKSCREEN);
        setTitleColor(Color.WHITE);

        initUi();
        initializeClickableEffect();
    }

    protected abstract String getSpellName();

    protected abstract String getSpellDescription();

    protected abstract int getManaCost();

    protected abstract ClickableEffectData getSpellEffectData();

    /**
     * Concrete effect logic for the spell.
     */
    protected abstract void applySpellEffect(HashMap<Integer, CustomBox> entities);

    private void initUi() {
        manaText = new Text(
                String.valueOf(getManaCost()),
                FontType.SILKSCREEN,
                0, 0,
                getZLayer(),
                Color.WHITE);

        String desc = getSpellDescription();
        if (desc != null && !desc.isEmpty()) {
            descText = new Text(
                    desc,
                    FontType.SILKSCREEN,
                    0, 0,
                    getZLayer(),
                    ColorSettings.TEXT_DEFAULT.getColor());
        }
        updateUiSizes();
    }

    private void updateUiSizes() {
        int h = getBounds().getHeight();
        int big = Math.max(8, (int) (h * 0.08f));
        if (manaText != null)
            manaText.withFontSize(big);

        if (descText != null) {
            int w = getBounds().getWidth();
            int marginX = Math.round(w * CardRenderUtils.DESC_MARGIN_X_PCT);
            int wrapW = Math.max(1, w - marginX * 2);
            int wrapH = Math.max(1, Math.round(h * CardRenderUtils.DESC_HEIGHT_PCT));
            descText.withWrapBounds(wrapW, wrapH).withAlignment(Align.center);
        }
    }

    @Override
    public void setBounds(Box bounds) {
        super.setBounds(bounds);
        updateUiSizes();
    }

    private void initializeClickableEffect() {
        setClickableEffect(
                (HashMap<Integer, CustomBox> entities) -> {
                    if (!trySpendMana())
                        return;
                    applySpellEffect(entities);
                    consume();
                },
                getSpellEffectData());
    }

    protected boolean trySpendMana() {
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        int cost = getManaCost();
        if (playerState == null || playerState.mana < cost) {
            Logger.log(
                    "SpellCard",
                    "Not enough mana. Need=" + cost + ", have=" + (playerState == null ? 0 : playerState.mana));
            return false;
        }
        playerState.mana -= cost;
        return true;
    }

    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null)
            return;
        this.onClick.run(interactionEntities);
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        if (alignment == PieceAlignment.P2
                && SettingsManager.debug.enableP2Bot
                && GameModeManager.getCurrent() != GameMode.LOCAL_MATCH) {
            return null;
        }

        if (alignment != GameContext.get().getTurnManager().getCurrentPlayer())
            return null;

        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        int cost = getManaCost();
        if (playerState == null || playerState.mana < cost)
            return null;

        return clickableEffectData;
    }

    @Override
    protected void renderExtraOverlays(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        int w = getWidth();
        int h = getHeight();

        // Mana
        if (manaText != null) {
            int tx = x + Math.round(w * CardRenderUtils.MANA_CX) - manaText.getWidth() / 2;
            int ty = y + Math.round(h * CardRenderUtils.MANA_CY) - manaText.getHeight() / 2;
            manaText.render(batch, zLevel, false, tx, ty);
        }

        // Description
        if (descText != null) {
            int marginX = Math.round(w * CardRenderUtils.DESC_MARGIN_X_PCT);
            int wrapW = Math.max(1, w - marginX * 2);
            int wrapH = Math.max(1, Math.round(h * CardRenderUtils.DESC_HEIGHT_PCT));

            descText.update();

            float textAreaBottomY = y + Math.round(h * CardRenderUtils.DESC_BOTTOM_Y_PCT);
            float textAreaCenterY = textAreaBottomY + wrapH / 2f;

            int tx = x + (w - descText.getWidth()) / 2;
            int ty = Math.round(textAreaCenterY - descText.getHeight() / 2f);

            descText.render(batch, zLevel, isPaused, tx, ty);
        }
    }
}
