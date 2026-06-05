package io.github.elderpath_crusade.ui_objects;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.PreviewCard;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.supers.LowestOrderTexture;

import java.util.ArrayList;
import java.util.List;

/**
 * UI overlay that shows a large, non-interactive card preview for the piece
 * under the mouse after a short hover delay.
 */
public class CardPreviewPanel extends LowestOrderTexture implements UIRenderable {
    private static final float HOVER_THRESHOLD_SEC = 0.6f;
    private static final int SCREEN_MARGIN = 190;
    private static final int PREVIEW_Z = 10;

    private String currentPieceId = null;
    private String previewedPieceId = null;
    private float hoverAccum = 0f;
    private PreviewCard previewCard = null;

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        if (isPaused || GameContext.get().getGameManager().isPaused()) {
            clearPreview();
            return;
        }
        Entity hovered = findHoveredEntity();
        if (hovered == null) {
            clearOrDecay();
            return;
        }
        String id = EntityUtils.getId(hovered);
        float dt = Gdx.graphics.getDeltaTime();
        if (!id.equals(currentPieceId)) {
            currentPieceId = id;
            hoverAccum = 0f;
        } else {
            hoverAccum += dt;
        }
        if (hoverAccum >= HOVER_THRESHOLD_SEC) {
            ensurePreviewFor(hovered);
            if (previewCard != null) {
                int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
                int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
                int height = Math.round(screenH * 0.40f);
                int width = Math.round(height * (125f / 200f));
                int x = screenW - width - SCREEN_MARGIN;
                int y = (screenH - height) / 2;
                if (previewCard.getWidth() != width || previewCard.getHeight() != height) {
                    previewCard.setBounds(new Box(0, 0, width, height));
                }
                previewCard.render(batch, PREVIEW_Z, false, x, y);
            }
        }
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        renderUI(batch, isPaused);
    }

    private void clearPreview() {
        currentPieceId = null;
        previewedPieceId = null;
        hoverAccum = 0f;
        previewCard = null;
    }

    private void clearOrDecay() {
        clearPreview();
    }

    private void ensurePreviewFor(Entity entity) {
        String id = EntityUtils.getId(entity);
        if (previewCard != null && previewedPieceId != null && previewedPieceId.equals(id)) {
            return;
        }
        previewedPieceId = id;
        String title = EntityUtils.getName(entity);
        if (title == null || title.isEmpty()) title = "Piece";
        int dummyW = 125, dummyH = 200;
        previewCard = new PreviewCard(0, 0, dummyW, dummyH, PREVIEW_Z, title,
                EntityUtils.getCost(entity), EntityUtils.getMaxHealth(entity),
                EntityUtils.getDamage(entity), EntityUtils.getSpeed(entity),
                EntityUtils.getActions(entity));
        previewCard.showFront();

        // Build description from data-driven ability definitions
        String pieceName = EntityUtils.getName(entity);
        PieceDefinition pieceDef = PieceRegistry.get(pieceName);
        if (pieceDef != null && !pieceDef.abilities().isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (String abilityName : pieceDef.abilities()) {
                AbilityDefinition abDef = AbilityRegistry.get(abilityName);
                if (abDef != null && abDef.description() != null && !abDef.description().isEmpty()) {
                    lines.add(abDef.description());
                }
            }
            previewCard.setDescription(lines.isEmpty() ? "" : String.join("\n\n", lines));
        } else {
            previewCard.setDescription("");
        }
    }

    private Entity findHoveredEntity() {
        Board b = GameContext.get().getActiveBoard();
        if (b == null) return null;

        int baseX = b.getBounds().getX();
        int baseY = b.getBounds().getY();
        int cellW = b.getPLOT_WIDTH();
        int cellH = b.getPLOT_HEIGHT();
        int boardW = cellW * b.getCOLS();
        int boardH = cellH * b.getROWS();

        int mouseX = Gdx.input.getX();
        int mouseY = GameContext.get().getSettingsManager().screenSize.getScreenHeight() - Gdx.input.getY();

        if (mouseX < baseX || mouseX >= baseX + boardW) return null;
        if (mouseY < baseY || mouseY >= baseY + boardH) return null;

        int col = (mouseX - baseX) / cellW;
        int row = (mouseY - baseY) / cellH;

        return b.getEntityAtPos(row, col);
    }
}
