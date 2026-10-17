package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.SpellDefinition;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import io.github.elderpath_crusade.server.ActionDispatcher;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.CardRenderUtils;
import io.github.elderpath_crusade.utils.ColorSettings;

import java.util.HashMap;

/**
 * Data-driven spell card. Handles targeting, mana cost, effect execution, and standard spell rendering.
 * Backed by a SpellDefinition loaded from spells.yaml (SpellRegistry) rather than hardcoded Java.
 */
public class SpellCard extends Card implements TargetFilter {

    protected final PieceAlignment alignment;
    private final String spellName;
    private final String description;
    private final int manaCost;
    private final SpellDefinition definition;

    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    private Text manaText;
    private Text descText;

    public SpellCard(
            PieceAlignment alignment,
            int x, int y, int width, int height, int z,
            String spellName,
            SpellDefinition definition) {
        super(x, y, width, height, z, null);
        this.alignment = alignment;
        this.spellName = spellName;
        this.definition = definition;
        this.description = definition.description();
        this.manaCost = definition.manaCost();

        setTitle(spellName, FontType.SILKSCREEN);
        setTitleColor(Color.WHITE);
        initUi();
        initializeClickableEffect();
    }

    public String getSpellName() { return spellName; }

    @Override
    public String getDisplayName() { return spellName; }
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
        if (definition.targeting() == null) {
            setClickableEffect(
                    (HashMap<Integer, CustomBox> entities) -> play(null, null),
                    ClickableEffectData.getImmediate());
            return;
        }

        setClickableEffect(
                (HashMap<Integer, CustomBox> entities) -> {
                    if (entities.get(1) instanceof Plot plot) play(plot.getRow(), plot.getCol());
                },
                ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1));
    }

    /** Sends this card's play to GameServer (or the host) — targetRow/targetCol null for an untargeted spell. */
    private void play(Integer targetRow, Integer targetCol) {
        String instanceId = getInstanceId();
        ActionDispatcher.dispatch(
                () -> new NetworkCommand.PlaySpellCard(alignment, instanceId, targetRow, targetCol),
                () -> GameContext.get().getGameServer().playSpellCard(alignment, instanceId, targetRow, targetCol)
        );
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
        if (!SummonCard.isLocallyControlled(alignment)) return null;
        if (!GameContext.get().getCardRules().canPlay(alignment, spellName)) return null;
        return clickableEffectData;
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (definition.targeting() == null) return false;
        if (!(box instanceof Plot plot)) return false;
        return GameContext.get().getCardRules().isValidSpellTarget(alignment, spellName, plot.getRow(), plot.getCol());
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
