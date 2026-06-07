package io.github.elderpath_crusade.game_objects.board.components;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Texture;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.rendering.TextureManager;
import io.github.elderpath_crusade.path_loaders.ImagePathSpritesAndAnimations;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.GraphicUtils;

import java.util.*;

/**
 * Handles the rendering of visual overlays on the board, such as HP bars and status effects.
 */
public class BoardOverlayRenderer {
    private final Board board;

    private static final Color HP_BG_COLOR = new Color(1f, 1f, 1f, 0.6f).mul(Color.RED);
    private static final int HP_PADDING_X = 2;
    private static final int HP_PADDING_Y = 1;
    private static final int HP_BG_PAD_X = 2;
    private static final int HP_BG_PAD_Y = 1;

    private final Map<String, Text> hpTexts = new HashMap<>();
    private final Map<String, Integer> hpCache = new HashMap<>();

    public BoardOverlayRenderer(Board board) {
        this.board = board;
    }

    /**
     * Render compact health overlay for a piece if it's damaged.
     */
    public void renderHpOverlay(SpriteBatch batch, int zLevel, int absX, int absY, Entity entity, Set<String> seen) {
        if (entity == null) return;

        int cur = EntityUtils.getCurrentHealth(entity);
        int max = EntityUtils.getMaxHealth(entity);
        if (cur >= max) return;

        String id = EntityUtils.getId(entity);
        seen.add(id);

        Text healthIndicatorText = hpTexts.get(id);
        String label = cur + "/" + max;
        int fontPx = Math.max(7, (int) (board.getPLOT_HEIGHT() * 0.16f));

        if (healthIndicatorText == null) {
            healthIndicatorText = new Text(label, FontType.WINDOW, 0, 0, zLevel + 3, Color.WHITE);
            healthIndicatorText.withFontSize(fontPx);
            hpTexts.put(id, healthIndicatorText);
            hpCache.put(id, cur);
        } else {
            Integer last = hpCache.get(id);
            if (last == null || last != cur) {
                healthIndicatorText.setText(label);
                healthIndicatorText.withFontSize(fontPx);
                hpCache.put(id, cur);
            }
        }

        if (!healthIndicatorText.getZs().contains(zLevel)) return;

        int textW = Math.max(1, healthIndicatorText.getWidth());
        int textH = Math.max(1, healthIndicatorText.getHeight());
        int tx = absX + HP_PADDING_X;
        int ty = absY + board.getPLOT_HEIGHT() - textH - HP_PADDING_Y;
        int bgX = tx - HP_BG_PAD_X;
        int bgY = ty - HP_BG_PAD_Y;
        int bgW = textW + HP_BG_PAD_X * 2;
        int bgH = textH + HP_BG_PAD_Y * 2;

        batch.draw(GraphicUtils.getPixelTexture(HP_BG_COLOR), bgX, bgY, bgW, bgH);
        healthIndicatorText.render(batch, zLevel, false, tx, ty);
    }

    /**
     * Removes cached UI elements for pieces that are no longer present.
     */
    public void cleanupStaleHpTexts(Set<String> seen) {
        if (hpTexts.isEmpty()) return;
        hpTexts.keySet().retainAll(seen);
        hpCache.keySet().retainAll(seen);
    }
}
