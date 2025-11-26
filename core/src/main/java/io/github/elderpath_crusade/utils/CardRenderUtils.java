package io.github.elderpath_crusade.utils;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import io.github.elderpath_crusade.ui_objects.Text;

import java.util.function.BiConsumer;

/**
 * Shared rendering logic for unit cards (SummonCard and PreviewCard).
 * Ensures consistent placement of stats orbs and description text.
 */
public class CardRenderUtils {

    // Normalized centers for stat orbs (0..1 relative to card size)
    // Tuned to the standard card template
    public static final float MANA_CX = 0.825f;
    public static final float MANA_CY = 0.890f;
    public static final float HP_CX = 0.160f;
    public static final float HP_CY = 0.130f;
    public static final float SPD_CX = 0.365f;
    public static final float SPD_CY = 0.110f;
    public static final float ACT_CX = 0.635f;
    public static final float ACT_CY = 0.110f;
    public static final float ATK_CX = 0.840f;
    public static final float ATK_CY = 0.130f;

    // Description text area layout
    public static final float DESC_MARGIN_X_PCT = 0.1f;
    public static final float DESC_HEIGHT_PCT = 0.16f;
    public static final float DESC_BOTTOM_Y_PCT = 0.22f;

    /**
     * Renders the standard unit card overlays: stats in orbs and description text.
     *
     * @param batch    SpriteBatch to draw to
     * @param zLevel   Z-index for rendering
     * @param isPaused Game pause state
     * @param x        Card absolute X
     * @param y        Card absolute Y
     * @param w        Card width
     * @param h        Card height
     * @param manaText Text object for Mana Cost
     * @param hpText   Text object for Health
     * @param spdText  Text object for Speed
     * @param actText  Text object for Actions
     * @param atkText  Text object for Attack
     * @param descText Text object for Description (nullable)
     */
    public static void renderUnitCardOverlays(
            SpriteBatch batch, int zLevel, boolean isPaused,
            int x, int y, int w, int h,
            Text manaText, Text hpText, Text spdText, Text actText, Text atkText,
            Text descText) {
        // Helper to center draw
        BiConsumer<Text, int[]> drawCentered = (t, center) -> {
            if (t == null)
                return;
            int tx = x + center[0] - t.getWidth() / 2;
            int ty = y + center[1] - t.getHeight() / 2;
            t.render(batch, zLevel, false, tx, ty);
        };

        // Compute centers in pixel space
        int[] manaC = new int[] { Math.round(w * MANA_CX), Math.round(h * MANA_CY) };
        int[] hpC = new int[] { Math.round(w * HP_CX), Math.round(h * HP_CY) };
        int[] spdC = new int[] { Math.round(w * SPD_CX), Math.round(h * SPD_CY) };
        int[] actC = new int[] { Math.round(w * ACT_CX), Math.round(h * ACT_CY) };
        int[] atkC = new int[] { Math.round(w * ATK_CX), Math.round(h * ATK_CY) };

        // Render stats
        drawCentered.accept(manaText, manaC);
        drawCentered.accept(hpText, hpC);
        drawCentered.accept(spdText, spdC);
        drawCentered.accept(actText, actC);
        drawCentered.accept(atkText, atkC);

        // Render description text
        if (descText != null) {
            int marginX = Math.round(w * DESC_MARGIN_X_PCT);
            int wrapW = Math.max(1, w - marginX * 2);
            int wrapH = Math.max(1, Math.round(h * DESC_HEIGHT_PCT));

            // Ensure wrapping matches current size each frame
            descText.withWrapBounds(wrapW, wrapH).withAlignment(Align.center);
            // Update text to get accurate height after wrapping
            descText.update();

            // Calculate text area bounds
            float textAreaBottomY = y + Math.round(h * DESC_BOTTOM_Y_PCT);
            float textAreaCenterY = textAreaBottomY + wrapH / 2f;

            // Center text vertically within the text area
            int tx = x + (w - descText.getWidth()) / 2;
            int ty = Math.round(textAreaCenterY - descText.getHeight() / 2f);

            descText.render(batch, zLevel, isPaused, tx, ty);
        }
    }
}
