package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.CardRenderUtils;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.Logger;
import io.github.elderpath_crusade.data_objects.Box;

import java.util.HashMap;

/**
 * Data-driven spell card. Handles targeting, mana cost, effect execution, and standard spell rendering.
 */
public class SpellCard extends Card implements TargetFilter {

    @FunctionalInterface
    public interface SpellEffect {
        void apply(Board board, Plot plot, PieceAlignment caster);
    }

    @FunctionalInterface
    public interface SpellTargetFilter {
        boolean test(Board board, Plot plot, PieceAlignment caster);
    }

    protected final Board board;
    protected final PieceAlignment alignment;
    private final String spellName;
    private final String description;
    private final int manaCost;
    private final SpellEffect effect;
    private final SpellTargetFilter targetFilter;

    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    private Text manaText;
    private Text descText;

    public SpellCard(
            Board board, PieceAlignment alignment,
            int x, int y, int width, int height, int z,
            String spellName, int manaCost, String description,
            SpellEffect effect,
            SpellTargetFilter targetFilter) {
        super(x, y, width, height, z, null);
        this.board = board;
        this.alignment = alignment;
        this.spellName = spellName;
        this.description = description;
        this.manaCost = manaCost;
        this.effect = effect;
        this.targetFilter = targetFilter;

        setTitle(spellName, FontType.SILKSCREEN);
        setTitleColor(Color.WHITE);
        initUi();
        initializeClickableEffect();
    }

    public String getSpellName() { return spellName; }
    public int getManaCost() { return manaCost; }

    private void initUi() {
        manaText = new Text(
                String.valueOf(manaCost),
                FontType.SILKSCREEN,
                0, 0,
                getZLayer(),
                Color.WHITE);

        if (description != null && !description.isEmpty()) {
            descText = new Text(
                    description,
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
                    CustomBox target = entities.get(1);
                    if (target instanceof Plot plot) {
                        effect.apply(board, plot, alignment);
                    }
                    consume();
                },
                ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1));
    }

    private boolean trySpendMana() {
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        if (playerState == null || playerState.mana < manaCost) {
            Logger.log(
                    "SpellCard",
                    "Not enough mana. Need=" + manaCost + ", have=" + (playerState == null ? 0 : playerState.mana));
            return false;
        }
        playerState.mana -= manaCost;
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
                && GameContext.get().getSettingsManager().debug.enableP2Bot
                && GameContext.get().getGameModeManager().getCurrent() != GameMode.LOCAL_MATCH) {
            return null;
        }

        if (alignment != GameContext.get().getTurnManager().getCurrentPlayer())
            return null;

        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        if (playerState == null || playerState.mana < manaCost)
            return null;

        return clickableEffectData;
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (box instanceof Plot plot) {
            return targetFilter.test(board, plot, alignment);
        }
        return false;
    }

    @Override
    protected void renderExtraOverlays(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        int w = getWidth();
        int h = getHeight();

        if (manaText != null) {
            int tx = x + Math.round(w * CardRenderUtils.MANA_CX) - manaText.getWidth() / 2;
            int ty = y + Math.round(h * CardRenderUtils.MANA_CY) - manaText.getHeight() / 2;
            manaText.render(batch, zLevel, false, tx, ty);
        }

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
