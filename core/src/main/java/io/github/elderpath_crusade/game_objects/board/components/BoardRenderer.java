package io.github.elderpath_crusade.game_objects.board.components;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
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
        GamePiece[][] gamePieces = board.getGamePieces();
        BoardIdentifierSymbol[] rowSymbols = board.getRowIdentifierSymbols();
        BoardIdentifierSymbol[] colSymbols = board.getColIdentifierSymbols();

        Set<UUID> seen = new HashSet<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                layout[row][col].render(batch, zLevel, isPaused, col * pw, row * ph);
                GamePiece gp = gamePieces[row][col];
                if (gp != null) {
                    overlayRenderer.renderHpOverlay(batch, zLevel, col * pw, row * ph, gp, seen);
                }
            }
        }
        GameContext.get().getPieceRenderSystem().render(batch, zLevel, isPaused, 0, 0, pw, ph);

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
        GamePiece[][] gamePieces = board.getGamePieces();
        BoardIdentifierSymbol[] rowSymbols = board.getRowIdentifierSymbols();
        BoardIdentifierSymbol[] colSymbols = board.getColIdentifierSymbols();

        Set<UUID> seen = new HashSet<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int absX = x + col * pw;
                int absY = y + row * ph;
                layout[row][col].render(batch, zLevel, isPaused, absX, absY);
                GamePiece gp = gamePieces[row][col];
                if (gp != null) {
                    overlayRenderer.renderHpOverlay(batch, zLevel, absX, absY, gp, seen);
                }
            }
        }
        GameContext.get().getPieceRenderSystem().render(batch, zLevel, isPaused, x, y, pw, ph);

        Arrays.stream(rowSymbols).forEach(s -> s.render(batch, zLevel, isPaused, x + s.getX(), y + s.getY()));
        Arrays.stream(colSymbols).forEach(s -> s.render(batch, zLevel, isPaused, x + s.getX(), y + s.getY()));
        overlayRenderer.cleanupStaleHpTexts(seen);
    }
}
