package io.github.elderpath_crusade.game_objects.board.components;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.ui_objects.BoardIdentifierSymbol;

import java.util.*;

/**
 * Handles all rendering logic for the Board: grid plots, piece HP overlays,
 * ECS piece rendering delegation, and board identifier symbols.
 */
public class BoardRenderer {
    private final Board board;
    private final BoardOverlayRenderer overlayRenderer;

    public BoardRenderer(Board board, BoardOverlayRenderer overlayRenderer) {
        this.board = board;
        this.overlayRenderer = overlayRenderer;
    }

    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        int rows = board.getROWS();
        int cols = board.getCOLS();
        int pw = board.getPLOT_WIDTH();
        int ph = board.getPLOT_HEIGHT();
        Renderable[][] layout = board.getLayout();
        BoardIdentifierSymbol[] rowSymbols = board.getRowIdentifierSymbols();
        BoardIdentifierSymbol[] colSymbols = board.getColIdentifierSymbols();

        Set<String> seen = new HashSet<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                layout[row][col].render(batch, zLevel, isPaused, col * pw, row * ph);
            }
        }
        GameContext.get().getPieceRenderSystem().render(batch, zLevel, isPaused, 0, 0, pw, ph);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Entity entity = board.getEntityAtPos(row, col);
                if (entity != null) {
                    overlayRenderer.renderHpOverlay(batch, zLevel, col * pw, row * ph, entity, seen);
                }
            }
        }

        Arrays.stream(rowSymbols).forEach(s -> s.render(batch, zLevel, isPaused));
        Arrays.stream(colSymbols).forEach(s -> s.render(batch, zLevel, isPaused));
        overlayRenderer.cleanupStaleHpTexts(seen);
    }

    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        int rows = board.getROWS();
        int cols = board.getCOLS();
        int pw = board.getPLOT_WIDTH();
        int ph = board.getPLOT_HEIGHT();
        Renderable[][] layout = board.getLayout();
        BoardIdentifierSymbol[] rowSymbols = board.getRowIdentifierSymbols();
        BoardIdentifierSymbol[] colSymbols = board.getColIdentifierSymbols();

        Set<String> seen = new HashSet<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int absX = x + col * pw;
                int absY = y + row * ph;
                layout[row][col].render(batch, zLevel, isPaused, absX, absY);
            }
        }
        GameContext.get().getPieceRenderSystem().render(batch, zLevel, isPaused, x, y, pw, ph);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Entity entity = board.getEntityAtPos(row, col);
                if (entity != null) {
                    int absX = x + col * pw;
                    int absY = y + row * ph;
                    overlayRenderer.renderHpOverlay(batch, zLevel, absX, absY, entity, seen);
                }
            }
        }

        Arrays.stream(rowSymbols).forEach(s -> s.render(batch, zLevel, isPaused, x + s.getX(), y + s.getY()));
        Arrays.stream(colSymbols).forEach(s -> s.render(batch, zLevel, isPaused, x + s.getX(), y + s.getY()));
        overlayRenderer.cleanupStaleHpTexts(seen);
    }
}
