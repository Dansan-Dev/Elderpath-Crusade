package io.github.elderpath_crusade.utils;

import com.badlogic.gdx.graphics.Texture;
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
    // UI-002 (user request, 2026-11-14): HP/Attack swapped sides — HP now sits where Attack
    // used to (bottom-right), Attack where HP used to (bottom-left). card_front.png's baked-in
    // gems were swapped to match (red HP gem bottom-right, green Attack gem bottom-left) — move
    // these and the art together, or each number lands in the wrong-colored gem.
    public static final float HP_CX = 0.840f;
    public static final float HP_CY = 0.130f;
    public static final float SPD_CX = 0.365f;
    public static final float SPD_CY = 0.110f;
    public static final float ACT_CX = 0.635f;
    public static final float ACT_CY = 0.110f;
    public static final float ATK_CX = 0.160f;
    public static final float ATK_CY = 0.130f;

    // Description text area layout
    public static final float DESC_MARGIN_X_PCT = 0.1f;
    public static final float DESC_HEIGHT_PCT = 0.16f;
    public static final float DESC_BOTTOM_Y_PCT = 0.22f;

    // UI-003 keyword badge row: centered at the top of the rules-text parchment. When a card has
    // badges, its rules text loses the top BADGE_ROW_PCT of its area so the two never overlap.
    public static final float BADGE_CY = 0.365f;
    public static final float BADGE_SIZE_PCT = 0.075f;   // of card height
    public static final float BADGE_ROW_PCT = 0.05f;
    // UI-003 Elder badge: centered above the portrait (user feedback, 2026-11-15 — originally
    // placed beside the cost gem). ELDER_CX mirrors Card.PORTRAIT_CENTER_X (0.508) rather than
    // importing it directly — utils stays generic, with no dependency on a concrete card class.
    public static final float ELDER_CX = 0.508f;
    public static final float ELDER_CY = 0.93f;
    public static final float ELDER_SIZE_PCT = 0.085f;   // of card height

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
        renderUnitCardOverlays(batch, zLevel, isPaused, x, y, w, h,
                manaText, hpText, spdText, actText, atkText, descText, false);
    }

    /** As above; {@code badgeRow} shrinks the rules-text area to leave room for keyword badges. */
    public static void renderUnitCardOverlays(
            SpriteBatch batch, int zLevel, boolean isPaused,
            int x, int y, int w, int h,
            Text manaText, Text hpText, Text spdText, Text actText, Text atkText,
            Text descText, boolean badgeRow) {
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
            int wrapH = Math.max(1, Math.round(h * (badgeRow ? DESC_HEIGHT_PCT - BADGE_ROW_PCT : DESC_HEIGHT_PCT)));

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

    /**
     * Draws UI-003's keyword badges as one centered row: each icon, then its label (if any).
     * Arrays are parallel and built once by the card; a null texture or label is skipped.
     */
    public static void renderKeywordBadges(SpriteBatch batch, int zLevel, int x, int y, int w, int h,
                                           Texture[] icons, Text[] labels) {
        if (icons == null || icons.length == 0) return;
        int size = Math.max(6, Math.round(h * BADGE_SIZE_PCT));
        int labelGap = Math.max(1, size / 4);
        int badgeGap = Math.max(2, size / 2);
        int total = 0;
        for (int i = 0; i < icons.length; i++) {
            if (i > 0) total += badgeGap;
            total += size;
            if (labels[i] != null) total += labelGap + labels[i].getWidth();
        }
        int cx = x + (w - total) / 2;
        int cy = y + Math.round(h * BADGE_CY);
        for (int i = 0; i < icons.length; i++) {
            if (i > 0) cx += badgeGap;
            if (icons[i] != null) batch.draw(icons[i], cx, cy - size / 2f, size, size);
            cx += size;
            Text label = labels[i];
            if (label != null) {
                cx += labelGap;
                label.render(batch, zLevel, false, cx, cy - label.getHeight() / 2);
                cx += label.getWidth();
            }
        }
    }

    /** Draws the Elder badge (UI-003) beside the cost gem; no-op when {@code elder} is null. */
    public static void renderElderBadge(SpriteBatch batch, int x, int y, int w, int h, Texture elder) {
        if (elder == null) return;
        int size = Math.max(6, Math.round(h * ELDER_SIZE_PCT));
        batch.draw(elder, x + w * ELDER_CX - size / 2f, y + h * ELDER_CY - size / 2f, size, size);
    }
}
