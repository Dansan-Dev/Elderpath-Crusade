package io.github.forest_of_dreams.ui_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;
import io.github.forest_of_dreams.game_objects.cards.PreviewCard;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.managers.GraphicsManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.LowestOrderTexture;
import io.github.forest_of_dreams.utils.HoverUtils;

import java.util.List;
import java.util.UUID;

/**
 * UI overlay that shows a large, non-interactive card preview for the monster piece
 * under the mouse after a short hover delay.
 */
public class CardPreviewPanel extends LowestOrderTexture implements UIRenderable {
    private static final float HOVER_THRESHOLD_SEC = 0.6f; // confirmed by user
    private static final int SCREEN_MARGIN = 20;
    private static final int PREVIEW_Z = 10; // internal z for card overlays

    private UUID currentPieceId = null;
    private float hoverAccum = 0f;
    private PreviewCard previewCard = null;

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        if (isPaused || GraphicsManager.isPaused()) {
            clearPreview();
            return;
        }
        MonsterGamePiece hovered = findHoveredMonster();
        if (hovered == null) {
            clearOrDecay();
            return;
        }
        UUID id = hovered.getId();
        float dt = Gdx.graphics.getDeltaTime();
        if (!id.equals(currentPieceId)) {
            // Switched hovered piece
            currentPieceId = id;
            hoverAccum = 0f;
        } else {
            hoverAccum += dt;
        }
        if (hoverAccum >= HOVER_THRESHOLD_SEC) {
            ensurePreviewFor(hovered);
            if (previewCard != null) {
                // Compute size as 40% of screen height, maintain 125:200 (w:h) aspect
                int screenW = SettingsManager.screenSize.getScreenWidth();
                int screenH = SettingsManager.screenSize.getScreenHeight();
                int height = Math.round(screenH * 0.40f);
                int width = Math.round(height * (125f / 200f));
                int x = screenW - width - SCREEN_MARGIN;
                int y = (screenH - height) / 2;
                // Update bounds if size changed (use setter so child sprites rescale correctly)
                if (previewCard.getWidth() != width || previewCard.getHeight() != height) {
                    previewCard.setBounds(new io.github.forest_of_dreams.data_objects.Box(0, 0, width, height));
                }
                // Render preview at computed position
                previewCard.render(batch, PREVIEW_Z, false, x, y);
            }
        }
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        // Ignore external offset; panel positions relative to screen size
        renderUI(batch, isPaused);
    }

    private void clearPreview() {
        currentPieceId = null;
        hoverAccum = 0f;
        previewCard = null;
    }

    private void clearOrDecay() {
        // If nothing is hovered, quickly hide preview by resetting state
        clearPreview();
    }

    private void ensurePreviewFor(MonsterGamePiece piece) {
        if (previewCard != null && currentPieceId != null && currentPieceId.equals(piece.getId())) {
            // Keep existing preview; nothing to rebuild
            return;
        }
        // Create a fresh preview card based on the piece stats
        String title = prettifyName(piece.getClass().getSimpleName());
        int dummyW = 125, dummyH = 200; // initial; resized on render
        previewCard = new PreviewCard(0, 0, dummyW, dummyH, PREVIEW_Z, title, piece.getStats());
        previewCard.showFront(); // ensure face-up
    }

    private String prettifyName(String simpleName) {
        // Remove common suffix if present
        if (simpleName.endsWith("Piece")) simpleName = simpleName.substring(0, simpleName.length()-5);
        return simpleName;
    }

    private MonsterGamePiece findHoveredMonster() {
        List<Renderable> renderables = GraphicsManager.getRenderables();
        for (Renderable r : renderables) {
            if (r instanceof Board b) {
                int rows = b.getROWS();
                int cols = b.getCOLS();
                int baseX = b.getBounds().getX();
                int baseY = b.getBounds().getY();
                int cellW = b.getPLOT_WIDTH();
                int cellH = b.getPLOT_HEIGHT();
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < cols; col++) {
                        int absX = baseX + col * cellW;
                        int absY = baseY + row * cellH;
                        if (HoverUtils.isHovered(absX, absY, cellW, cellH)) {
                            GamePiece gp = b.getGamePieceAtPos(row, col);
                            if (gp instanceof MonsterGamePiece mgp) {
                                return mgp;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
