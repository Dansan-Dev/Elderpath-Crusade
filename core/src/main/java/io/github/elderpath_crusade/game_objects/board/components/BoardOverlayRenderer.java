package io.github.elderpath_crusade.game_objects.board.components;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Texture;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.rendering.TextureManager;
import io.github.elderpath_crusade.game.TurnManager;
import io.github.elderpath_crusade.path_loaders.ImagePathSpritesAndAnimations;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.GraphicUtils;

import java.util.*;

/**
 * Handles the rendering of visual overlays on the board, such as HP bars and
 * status effects.
 */
public class BoardOverlayRenderer {
    private final Board board;

    // OPT-002: reusable to avoid per-frame allocation
    private static final Color STUN_TINT = new Color(1f, 0.22f, 0.71f, 1f);
    private static final Color DARKEN_TINT = new Color(0.6f, 0.6f, 0.6f, 1.0f);

    // Cached UI elements for compact health overlays on damaged pieces
    private final Map<UUID, Text> hpTexts = new HashMap<>();
    private final Map<UUID, Integer> hpCache = new HashMap<>();

    // Semi-transparent dark background for HP label
    private static final Color HP_BG_COLOR = new Color(1f, 1f, 1f, 0.6f).mul(Color.RED);
    private static final int HP_PADDING_X = 2;
    private static final int HP_PADDING_Y = 1;
    private static final int HP_BG_PAD_X = 2;
    private static final int HP_BG_PAD_Y = 1;

    public BoardOverlayRenderer(Board board) {
        this.board = board;
    }

    /**
     * Render a piece sprite with status effect tinting (stun or exhaustion).
     */
    public void renderPieceWithStatusEffects(SpriteBatch batch, int zLevel, int absX, int absY, GamePiece gp) {
        if (!(gp instanceof MonsterGamePiece mgp)) {
            gp.getSprite().render(batch, zLevel, false, absX, absY);
            return;
        }

        Color originalColor = batch.getColor().cpy();

        if (mgp.isStunned()) {
            batch.setColor(STUN_TINT);
            gp.getSprite().render(batch, zLevel, false, absX, absY);
            batch.setColor(originalColor);
            renderStunSymbol(batch, zLevel, absX, absY);
        } else if (mgp.isExhausted()) {
            PieceAlignment currentPlayer = GameContext.get().getTurnManager().getCurrentPlayer();
            if (mgp.getAlignment() == currentPlayer) {
                batch.setColor(DARKEN_TINT);
                gp.getSprite().render(batch, zLevel, false, absX, absY);
                batch.setColor(originalColor);
            } else {
                gp.getSprite().render(batch, zLevel, false, absX, absY);
            }
        } else {
            gp.getSprite().render(batch, zLevel, false, absX, absY);
        }
    }

    /**
     * Render the stun symbol overlay on top of a stunned piece.
     */
    private void renderStunSymbol(SpriteBatch batch, int zLevel, int absX, int absY) {
        Texture stunTexture = GameContext.get().getTextureManager().getTexture(ImagePathSpritesAndAnimations.STUN.getPath());
        if (stunTexture == null)
            return;

        int symbolSize = Math.min(board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()) * 3 / 5;
        int symbolX = absX + (board.getPLOT_WIDTH() - symbolSize) / 2;
        int symbolY = absY + (board.getPLOT_HEIGHT() - symbolSize) / 2;

        batch.draw(stunTexture, symbolX, symbolY, symbolSize, symbolSize);
    }

    /**
     * Render compact health overlay for a piece if it's damaged.
     */
    public void renderHpOverlay(SpriteBatch batch, int zLevel, int absX, int absY, GamePiece gp, Set<UUID> seen) {
        if (!(gp instanceof MonsterGamePiece mgp))
            return;

        GamePieceStats st = mgp.getStats();
        int cur = st.getCurrentHealth();
        int max = mgp.getEffectiveMaxHealth();
        if (cur >= max)
            return;

        UUID id = mgp.getId();
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

        if (!healthIndicatorText.getZs().contains(zLevel))
            return;

        int tx = absX + HP_PADDING_X;
        int ty = absY + HP_PADDING_Y;
        int textW = Math.max(1, healthIndicatorText.getWidth());
        int textH = Math.max(1, healthIndicatorText.getHeight());
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
    public void cleanupStaleHpTexts(Set<UUID> seen) {
        if (hpTexts.isEmpty())
            return;
        hpTexts.keySet().retainAll(seen);
        hpCache.keySet().retainAll(seen);
    }
}
