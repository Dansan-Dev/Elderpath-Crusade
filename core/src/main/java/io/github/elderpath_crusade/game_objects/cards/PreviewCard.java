package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import com.badlogic.gdx.utils.Align;
import io.github.elderpath_crusade.utils.CardRenderUtils;

/**
 * Non-interactive large card preview used by the hover panel.
 * Shows title and five stats inside the standard orbs.
 */
public class PreviewCard extends Card {
    private final GamePieceStats stats;
    private Text manaText; // cost
    private Text hpText;
    private Text spdText;
    private Text actText;
    private Text atkText;
    // Optional description area
    private Text descText;

    public PreviewCard(int x, int y, int width, int height, int z, String title, GamePieceStats stats) {
        super(x, y, width, height, z, null);
        this.stats = stats;
        setTitle(title, FontType.SILKSCREEN);
        setTitleColor(Color.WHITE);
        initTexts();
    }

    public void setDescription(String desc) {
        if (desc == null || desc.isEmpty()) {
            this.descText = null;
            return;
        }
        if (this.descText == null) {
            this.descText = new Text(
                desc,
                FontType.SILKSCREEN,
                0, 0,
                getZLayer(),
                ColorSettings.TEXT_DEFAULT.getColor()
            );
        } else {
            this.descText.setText(desc);
            this.descText.update();
        }
    }

    private void initTexts() {
        Color c = Color.WHITE;
        int z = getZLayer();
        manaText = new Text(String.valueOf(stats.getCost()), FontType.SILKSCREEN, 0, 0, z, c);
        hpText = new Text(String.valueOf(stats.getMaxHealth()), FontType.SILKSCREEN, 0, 0, z, c);
        spdText = new Text(String.valueOf(stats.getSpeed()), FontType.SILKSCREEN, 0, 0, z, c);
        actText = new Text(String.valueOf(stats.getActions()), FontType.SILKSCREEN, 0, 0, z, c);
        atkText = new Text(String.valueOf(stats.getDamage()), FontType.SILKSCREEN, 0, 0, z, c);
        updateTextSizes();
    }

    private void updateTextSizes() {
        int h = getBounds().getHeight();
        // Match SummonCard sizing factors
        int big = Math.max(8, (int) (h * 0.08f));
        int small = Math.max(8, (int) (h * 0.06f));
        if (manaText != null) manaText.withFontSize(big);
        if (hpText != null) hpText.withFontSize(big);
        if (atkText != null) atkText.withFontSize(big);
        if (spdText != null) spdText.withFontSize(small);
        if (actText != null) actText.withFontSize(small);
        // descText uses wrap + auto-scale, sizing handled per-frame in render
    }

    @Override
    public void setBounds(Box bounds) {
        super.setBounds(bounds);
        updateTextSizes();
        // Update description wrap bounds on resize (consistent with SummonCard)
        if (descText != null) {
            int w = getBounds().getWidth();
            int h = getBounds().getHeight();
            int marginX = Math.round(w * CardRenderUtils.DESC_MARGIN_X_PCT);
            int wrapW = Math.max(1, w - marginX * 2);
            int wrapH = Math.max(1, Math.round(h * CardRenderUtils.DESC_HEIGHT_PCT));
            descText.withWrapBounds(wrapW, wrapH).withAlignment(Align.center);
        }
    }

    @Override
    protected void renderExtraOverlays(SpriteBatch batch, int zLevel, boolean isPaused, int baseX, int baseY) {
        CardRenderUtils.renderUnitCardOverlays(
                batch, zLevel, isPaused,
                baseX, baseY, getWidth(), getHeight(),
                manaText, hpText, spdText, actText, atkText,
                descText);
    }

    // Non-interactive: never returns a click effect
    @Override
    public ClickableEffectData getClickableEffectData() {
        return null;
    }
}
