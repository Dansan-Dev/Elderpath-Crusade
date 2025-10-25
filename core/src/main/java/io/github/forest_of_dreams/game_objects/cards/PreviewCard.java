package io.github.forest_of_dreams.game_objects.cards;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.game_objects.board.GamePieceStats;
import io.github.forest_of_dreams.ui_objects.Text;

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

    public PreviewCard(int x, int y, int width, int height, int z, String title, GamePieceStats stats) {
        super(x, y, width, height, z, null);
        this.stats = stats;
        setTitle(title, FontType.SILKSCREEN);
        setTitleColor(Color.WHITE);
        initTexts();
    }

    private void initTexts() {
        Color c = Color.WHITE;
        int z = getZLayer();
        manaText = new Text(String.valueOf(stats.getCost()), FontType.SILKSCREEN, 0, 0, z, c);
        hpText   = new Text(String.valueOf(stats.getMaxHealth()), FontType.SILKSCREEN, 0, 0, z, c);
        spdText  = new Text(String.valueOf(stats.getSpeed()), FontType.SILKSCREEN, 0, 0, z, c);
        actText  = new Text(String.valueOf(stats.getActions()), FontType.SILKSCREEN, 0, 0, z, c);
        atkText  = new Text(String.valueOf(stats.getDamage()), FontType.SILKSCREEN, 0, 0, z, c);
        updateTextSizes();
    }

    private void updateTextSizes() {
        int h = getBounds().getHeight();
        int big = Math.max(10, (int)(h * 0.10f));
        int small = Math.max(8, (int)(h * 0.075f));
        if (manaText != null) manaText.withFontSize(big);
        if (hpText != null) hpText.withFontSize(big);
        if (atkText != null) atkText.withFontSize(big);
        if (spdText != null) spdText.withFontSize(small);
        if (actText != null) actText.withFontSize(small);
    }

    @Override
    public void setBounds(Box bounds) {
        super.setBounds(bounds);
        updateTextSizes();
    }

    @Override
    protected void renderExtraOverlays(SpriteBatch batch, int zLevel, boolean isPaused, int baseX, int baseY) {
        if (manaText == null) return;
        int w = getBounds().getWidth();
        int h = getBounds().getHeight();
        // Normalized positions tuned to default template
        float MANA_CX = 0.825f, MANA_CY = 0.890f;   // top-right big orb
        float HP_CX   = 0.160f, HP_CY   = 0.130f;   // bottom-left big orb
        float SPD_CX  = 0.365f, SPD_CY  = 0.110f;   // bottom row small (second-left)
        float ACT_CX  = 0.635f, ACT_CY  = 0.110f;   // bottom row small (second-right)
        float ATK_CX  = 0.840f, ATK_CY  = 0.130f;   // bottom-right big orb
        // Draw centered helper
        renderCenteredText(batch, zLevel, manaText, baseX + Math.round(w * MANA_CX), baseY + Math.round(h * MANA_CY));
        renderCenteredText(batch, zLevel, hpText,   baseX + Math.round(w * HP_CX),   baseY + Math.round(h * HP_CY));
        renderCenteredText(batch, zLevel, spdText,  baseX + Math.round(w * SPD_CX),  baseY + Math.round(h * SPD_CY));
        renderCenteredText(batch, zLevel, actText,  baseX + Math.round(w * ACT_CX),  baseY + Math.round(h * ACT_CY));
        renderCenteredText(batch, zLevel, atkText,  baseX + Math.round(w * ATK_CX),  baseY + Math.round(h * ATK_CY));
    }

    private static void renderCenteredText(SpriteBatch batch, int zLevel, Text t, int cx, int cy) {
        if (t == null) return;
        int tx = cx - t.getWidth()/2;
        int ty = cy - t.getHeight()/2;
        t.render(batch, zLevel, false, tx, ty);
    }

    // Non-interactive: never returns a click effect
    @Override
    public io.github.forest_of_dreams.data_objects.ClickableEffectData getClickableEffectData() {
        return null;
    }
}
