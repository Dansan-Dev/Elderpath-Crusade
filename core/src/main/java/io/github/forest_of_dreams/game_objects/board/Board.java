package io.github.forest_of_dreams.game_objects.board;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.enums.*;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.managers.InteractionManager;
import io.github.forest_of_dreams.managers.ZIndexRegistry;
import io.github.forest_of_dreams.managers.TurnManager;
import io.github.forest_of_dreams.multiplayer.EventBus;
import io.github.forest_of_dreams.multiplayer.GameEventType;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.utils.ColorSettings;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.ui_objects.BoardIdentifierSymbol;
import io.github.forest_of_dreams.utils.GraphicUtils;
import io.github.forest_of_dreams.utils.Logger;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.IntStream;

public class Board extends HigherOrderTexture {
    @Getter private final int ROWS;
    @Getter private final int COLS;
    @Getter private final int PLOT_WIDTH;
    @Getter private final int PLOT_HEIGHT;
    private final Renderable[][] board;
    private final GamePiece [][] gamePieces;
    private final BoardIdentifierSymbol[] rowIdentifierSymbols;
    private final BoardIdentifierSymbol[] colIdentifierSymbols;

    /** Notify all monster pieces on this board that a turn has started for the given player. */
    public void notifyTurnStartedForPieces(PieceAlignment player) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                GamePiece gp = gamePieces[r][c];
                if (gp instanceof MonsterGamePiece mgp) {
                    try { mgp.notifyTurnStarted(player); } catch (Exception ignored) {}
                }
            }
        }
    }

    /** Notify all monster pieces on this board that a turn has ended for the given player. */
    public void notifyTurnEndedForPieces(PieceAlignment player) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                GamePiece gp = gamePieces[r][c];
                if (gp instanceof MonsterGamePiece mgp) {
                    try { mgp.notifyTurnEnded(player); } catch (Exception ignored) {}
                }
            }
        }
    }

    // Cached UI elements for compact health overlays on damaged pieces
    private final Map<UUID, Text> hpTexts = new HashMap<>();
    private final Map<UUID, Integer> hpCache = new HashMap<>();
    // Semi-transparent dark background for HP label to avoid being obscured by later draws
    private static final Color HP_BG_COLOR = new Color(1f, 1f, 1f, 0.6f).mul(Color.RED);
    private static final int HP_PADDING_X = 2; // offset from plot corner
    private static final int HP_PADDING_Y = 1; // offset from plot corner
    private static final int HP_BG_PAD_X = 2;  // padding around text inside bg box
    private static final int HP_BG_PAD_Y = 1;  // padding around text inside bg box

    public Board(int x, int y, int plot_width, int plot_height, int rows, int cols) {
        ROWS = rows;
        COLS = cols;
        PLOT_WIDTH = plot_width;
        PLOT_HEIGHT = plot_height;
        rowIdentifierSymbols = new BoardIdentifierSymbol[ROWS];
        colIdentifierSymbols = new BoardIdentifierSymbol[COLS];
        board = new Renderable[ROWS][COLS];
        gamePieces = new GamePiece[ROWS][COLS];
        setBounds(new Box(x, y, PLOT_WIDTH*COLS, PLOT_HEIGHT*ROWS));

        Arrays.stream(gamePieces).forEach(a -> Arrays.fill(a, null));
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Renderable renderable = EmptyTexture.get(PLOT_WIDTH*col, PLOT_HEIGHT*row, PLOT_WIDTH, PLOT_HEIGHT);
                board[row][col] = renderable;
            }
        }
        setBoardIdentifierSymbols();
    }

    // --- Compact health overlay helpers ---
    private void renderHpOverlay(SpriteBatch batch, int zLevel, int absX, int absY, GamePiece gp, Set<UUID> seen) {
        if (!(gp instanceof MonsterGamePiece mgp)) return;
        GamePieceStats st = mgp.getStats();
        int cur = st.getCurrentHealth();
        int max = st.getMaxHealth();
        if (cur >= max) return; // full health -> no overlay
        UUID id = mgp.getId();
        seen.add(id);
        Text healthIndicatorText = hpTexts.get(id);
        String label = cur + "/" + max;
        int fontPx = Math.max(7, (int)(PLOT_HEIGHT * 0.16f));
        if (healthIndicatorText == null) {
            healthIndicatorText = new Text(label, FontType.WINDOW, 0, 0, zLevel+3, Color.WHITE);
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
        // Only render overlay elements during the text's own z-layer pass to avoid overdraw ordering issues
        if (!healthIndicatorText.getZs().contains(zLevel)) {
            return;
        }
        // Position at bottom-left of plot with padding
        int tx = absX + HP_PADDING_X;
        int ty = absY + HP_PADDING_Y;
        // Background behind text to improve readability and visual cohesion
        int textW = Math.max(1, healthIndicatorText.getWidth());
        int textH = Math.max(1, healthIndicatorText.getHeight());
        int bgX = tx - HP_BG_PAD_X;
        int bgY = ty - HP_BG_PAD_Y;
        int bgW = textW + HP_BG_PAD_X * 2;
        int bgH = textH + HP_BG_PAD_Y * 2;
        batch.draw(GraphicUtils.getPixelTexture(HP_BG_COLOR), bgX, bgY, bgW, bgH);
        // Render text on top
        healthIndicatorText.render(batch, zLevel, false, tx, ty);
    }

    private void cleanupStaleHpTexts(Set<UUID> seen) {
        if (hpTexts.isEmpty()) return;
        Iterator<Map.Entry<UUID, Text>> it = hpTexts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Text> e = it.next();
            if (!seen.contains(e.getKey())) {
                it.remove();
                hpCache.remove(e.getKey());
            }
        }
    }

    public class Position {
        @Getter private final Board board;
        @Getter @Setter private int row;
        @Getter @Setter private int col;

        public Position(Board board, int row, int col) {
            this.board = board;
            this.row = row;
            this.col = col;
        }

        public boolean isValid(int row, int col) {
            return row >= 0 && row < board.ROWS && col >= 0 && col < COLS;
        }
    }

    public void initializePlots() {
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Plot plot = new Plot(0, 0, PLOT_WIDTH, PLOT_HEIGHT);
                if (row == 0) plot.withPlotColor(ColorSettings.PLOT_PLAYER_1_ROW.getColor());
                if (row == ROWS - 1) plot.withPlotColor(ColorSettings.PLOT_PLAYER_2_ROW.getColor());
                plot.setBoard(this);
                plot.setClickableEffect(
                    this::handlePlotMove,
                    ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1)
                );
                replacePlotAtPos(row, col, plot);
            }
        }
        // Ensure Board is re-indexed for z-bucket rendering after plots are initialized
        ZIndexRegistry.notifyZChanged(this);
    }

    public int[] getPixelSize() {
        return new int[]{PLOT_WIDTH*COLS, PLOT_HEIGHT*ROWS};
    }

    private char toLetter(int n) {
        if (n < 0 || n > 25) throw new IllegalArgumentException("n must be in range [0, 25]");
        return (char) ('A' + n);
    }

    private void checkBoardPosition(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            throw new IllegalArgumentException("row must be in [0, " + (ROWS - 1) + "] and col must be in [0, " + (COLS - 1) + "]");
        }
    }

    public void setBoardIdentifierSymbols() {
        IntStream.iterate(0, i -> i + 1).limit(ROWS)
            .forEach(i -> rowIdentifierSymbols[i] = new BoardIdentifierSymbol(
                String.valueOf(toLetter(i)),
                -PLOT_WIDTH/4,
                PLOT_HEIGHT/2+PLOT_HEIGHT*i,
                GRID_DIRECTION.ROW,
                true
            ));
        IntStream.iterate(0, i -> i + 1).limit(COLS)
            .forEach(i -> colIdentifierSymbols[i] = new BoardIdentifierSymbol(
                String.valueOf(i+1),
                (PLOT_WIDTH)/2+PLOT_WIDTH*i,
                -PLOT_HEIGHT/4,
                GRID_DIRECTION.COLUMN,
                true
            ));
        // Board now contains label Texts at z=0; ensure z-buckets reindex
        ZIndexRegistry.notifyZChanged(this);
    }

    // Update plot highlighting by comparing this board's plots with the InteractionManager's active targets.
    private void updatePlotHighlights() {
        boolean active = InteractionManager.hasActiveSelection();
        List<CustomBox> targets = InteractionManager.getActiveTargets();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Renderable r = board[row][col];
                if (r instanceof Plot p) {
                    boolean shouldHighlight = false;
                    if (active && !targets.isEmpty()) {
                        for (CustomBox b : targets) {
                            if (b == p) { shouldHighlight = true; break; }
                        }
                    }
                    p.setHighlighted(shouldHighlight);
                }
            }
        }
    }

    // Mark candidate move plots (white dots) and attack plots (red glow) when a movement source is active
    private void updateCandidateMoveSpots() {
        Object src = InteractionManager.getActiveSource();
        // Clear all by default
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Renderable r = board[row][col];
                if (r instanceof Plot p) {
                    p.setCandidate(false);
                    p.setAttackCandidate(false);
                }
            }
        }
        if (!(src instanceof Plot plot)) return;
        // Ensure the source plot belongs to this board
        int[] sIdx = getIndicesOfPlot(plot);
        if (sIdx == null) return;
        int sr = sIdx[0], sc = sIdx[1];
        GamePiece gp = getGamePieceAtPos(sr, sc);
        if (!(gp instanceof MonsterGamePiece mgp)) return;
        if (mgp.getAlignment() != TurnManager.getCurrentPlayer()) return;
        int speed = mgp.getStats().getSpeed();
        List<Plot> reachable = getReachablePlots(sr, sc, speed);
        List<Plot> attackables = getAdjacentHostilePlots(sr, sc, ((MonsterGamePiece) gp).getAlignment());
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Renderable r = board[row][col];
                if (r instanceof Plot p) {
                    boolean isAttack = false;
                    for (Plot a : attackables) { if (a == p) { isAttack = true; break; } }
                    if (isAttack) {
                        p.setAttackCandidate(true);
                        p.setCandidate(false); // no dot on enemies
                        continue;
                    }
                    boolean moveCand = false;
                    for (Plot q : reachable) { if (q == p) { moveCand = true; break; } }
                    p.setCandidate(moveCand);
                }
            }
        }
    }

    public Renderable getPlotAtPos(int row, int col) {
        return board[row][col];
    }

    public GamePiece getGamePieceAtPos(int row, int col) {
        return gamePieces[row][col];
    }

    /**
     * Resolve the GamePiece currently occupying the given Plot instance, or null if none.
     */
    public GamePiece getGamePieceAtPlot(Plot plot) {
        if (plot == null) return null;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (board[row][col] == plot) {
                    return gamePieces[row][col];
                }
            }
        }
        return null;
    }

    /**
     * Generalized summon target validation: a plot is valid if it is empty and lies on the
     * appropriate summon row for the given alignment (P1 → first row 0, P2 → last row ROWS-1).
     */
    public boolean isValidSummonTarget(Plot plot, io.github.forest_of_dreams.enums.PieceAlignment alignment) {
        if (plot == null || alignment == null) return false;
        int[] idx = getIndicesOfPlot(plot);
        if (idx == null) return false;
        // must be empty
        if (getGamePieceAtPos(idx[0], idx[1]) != null) return false;
        // row policy
        switch (alignment) {
            case P1:
                return idx[0] == 0;
            case P2:
                return idx[0] == ROWS - 1;
            default:
                return false;
        }
    }

    /**
     * Find the grid indices of a given Plot instance.
     * @return int[]{row, col} if found, otherwise null.
     */
    public int[] getIndicesOfPlot(Plot plot) {
        if (plot == null) return null;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (board[row][col] == plot) {
                    return new int[]{row, col};
                }
            }
        }
        return null;
    }

    public void removePlotAtPos(int row, int col) {
        Renderable renderable = board[row][col];
        getRenderables().remove(renderable);
        board[row][col] = EmptyTexture.get(PLOT_WIDTH*col, PLOT_HEIGHT*row, PLOT_WIDTH, PLOT_HEIGHT);
    }

    public void removeGamePieceAtPos(int row, int col) {
        setGamePiecePos(row, col, null);
    }

    public void setGamePiecePos(int row, int col, GamePiece gamePiece) {
        checkBoardPosition(row, col);
        gamePieces[row][col] = gamePiece;
        // A piece sprite affects z coverage; re-index Board
        ZIndexRegistry.notifyZChanged(this);
    }

    public void moveGamePiece(int currentRow, int currentCol, int newRow, int newCol) {
        GamePiece gamePiece = gamePieces[currentRow][currentCol];
        setGamePiecePos(currentRow, currentCol, null);
        setGamePiecePos(newRow, newCol, gamePiece);
    }

    public void addGamePieceToPos(int row, int col, GamePiece gamePiece) {
        setGamePiecePos(row, col, gamePiece);
        gamePiece.updateData(GamePieceData.POSITION, new Position(this, row, col));
        // Notify abilities on spawn
        if (gamePiece instanceof MonsterGamePiece mgp) {
            mgp.notifySpawned(row, col);
        }
        // Emit PIECE_SPAWNED when a piece is added to the board
        EventBus.emit(
                GameEventType.PIECE_SPAWNED,
                Map.of(
                        "pieceId", gamePiece.getId().toString(),
                        "owner", gamePiece.getAlignment().name(),
                        "row", row,
                        "col", col
                )
        );
    }

    private void replacePlotAtPos(int row, int col, Renderable newRenderable) {
        if (newRenderable.getBounds().getWidth() != PLOT_WIDTH
            || newRenderable.getBounds().getHeight() != PLOT_HEIGHT) throw new IllegalArgumentException("Renderable must be in PLOT size");

        Renderable renderable = board[row][col];
        getRenderables().remove(renderable);

        // Set the child's relative position within the board grid for correct hit-testing
        if (newRenderable.getBounds() != null) {
            newRenderable.getBounds().setX(col * PLOT_WIDTH);
            newRenderable.getBounds().setY(row * PLOT_HEIGHT);
        }
        newRenderable.setParent(getBounds());
        board[row][col] = newRenderable;
        getRenderables().add(newRenderable);

        // If this is a Plot, wire it for movement multi-interaction
        if (newRenderable instanceof Plot plot) {
            plot.setBoard(this);
            plot.setClickableEffect(
                this::handlePlotMove,
                ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1)
            );
        }
        // Board's z coverage may have changed; re-index in z-buckets
        ZIndexRegistry.notifyZChanged(this);
    }

    // Helpers for movement reachability and occupancy
    public boolean isOccupied(int row, int col) {
        return getGamePieceAtPos(row, col) != null;
    }

    /**
     * Compute reachable plots from (row,col) within a maximum path length (speed),
     * moving 4-directionally (N/E/S/W). Cannot pass through or end on occupied cells.
     * The origin cell is excluded from the results.
     */
    public List<Plot> getReachablePlots(int row, int col, int speed) {
        List<Plot> out = new ArrayList<>();
        if (speed <= 0) return out;
        boolean[][] visited = new boolean[ROWS][COLS];
        int[][] dist = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) Arrays.fill(dist[r], -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{row, col});
        visited[row][col] = true;
        dist[row][col] = 0;
        int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
        while (!q.isEmpty()) {
            int[] cur = q.removeFirst();
            int cr = cur[0], cc = cur[1];
            int cd = dist[cr][cc];
            if (cd >= speed) continue; // cannot step further
            for (int[] d : dirs) {
                int nr = cr + d[0];
                int nc = cc + d[1];
                if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS) continue;
                if (visited[nr][nc]) continue;
                // Block stepping into occupied cells
                if (isOccupied(nr, nc)) continue;
                visited[nr][nc] = true;
                dist[nr][nc] = cd + 1;
                q.addLast(new int[]{nr, nc});
                // Exclude origin (handled by cd>=0 and origin has dist 0)
                if (!(nr == row && nc == col)) {
                    Renderable r = board[nr][nc];
                    if (r instanceof Plot p) out.add(p);
                }
            }
        }
        return out;
    }

    /** Return adjacent hostile plots (cardinal) around (row,col). */
    public List<Plot> getAdjacentHostilePlots(int row, int col, PieceAlignment friendlyAlignment) {
        List<Plot> out = new ArrayList<>();
        int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS) continue;
            GamePiece gp = getGamePieceAtPos(nr, nc);
            if (gp instanceof MonsterGamePiece mgp) {
                        if (mgp.getAlignment() != friendlyAlignment) {
                    Renderable r = board[nr][nc];
                    if (r instanceof Plot p) out.add(p);
                }
                // Future: handle opposite case if playing as P2, etc.
            }
        }
        return out;
    }

    /**
     * Computes the effective attack damage for an attacker at a given source cell.
     * Hook for future ability/buff modifiers; currently returns base stats damage.
     */
    private int getAttackDamage(MonsterGamePiece attacker, int srcRow, int srcCol) {
        if (attacker == null) return 0;
        return attacker.getStats().getDamage();
    }

    public void resetActionsForOwner(PieceAlignment owner) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                GamePiece gp = gamePieces[r][c];
                if (gp instanceof MonsterGamePiece mgp && mgp.getAlignment() == owner) {
                    mgp.updateData(GamePieceData.ACTIONS_REMAINING, mgp.getStats().getActions());
                }
            }
        }
    }

    private int getRemainingActions(MonsterGamePiece mgp) {
        Object v = mgp.getData(GamePieceData.ACTIONS_REMAINING);
        if (v instanceof Integer n) return n;
        return mgp.getStats().getActions();
    }

    private void spendAction(MonsterGamePiece mgp) {
        int left = Math.max(0, getRemainingActions(mgp) - 1);
        mgp.updateData(GamePieceData.ACTIONS_REMAINING, left);
        // Emit ACTION_SPENT with remaining actions
        EventBus.emit(
                GameEventType.ACTION_SPENT,
                Map.of(
                        "pieceId", mgp.getId().toString(),
                        "owner", mgp.getAlignment().name(),
                        "remaining", left
                )
        );
    }

    private void handlePlotMove(HashMap<Integer, CustomBox> entities) {
        Object s = entities.get(0);
        Object t = entities.get(1);
        if (!(s instanceof Plot src) || !(t instanceof Plot dst)) return;
        int[] sIdx = getIndicesOfPlot(src);
        int[] dIdx = getIndicesOfPlot(dst);
        if (sIdx == null || dIdx == null) return;
        int sr = sIdx[0], sc = sIdx[1];
        int dr = dIdx[0], dc = dIdx[1];
        GamePiece gp = getGamePieceAtPos(sr, sc);
        if (!(gp instanceof MonsterGamePiece mgp)) return;
        if (mgp.getAlignment() != TurnManager.getCurrentPlayer()) return;
        // Must have actions remaining
        if (getRemainingActions(mgp) <= 0) return;

        // Attack branch: adjacent hostile in 4-dir
        GamePiece targetPiece = getGamePieceAtPos(dr, dc);
        int manhattan = Math.abs(dr - sr) + Math.abs(dc - sc);
        if (targetPiece instanceof MonsterGamePiece enemy && enemy.getAlignment() != mgp.getAlignment() && manhattan == 1) {
            int dmg = getAttackDamage(mgp, sr, sc);
            enemy.getStats().dealDamage(dmg);
            // Ability notifications
            try { mgp.notifyAttack(enemy, dmg); } catch (Exception ignored) {}
            try { enemy.notifyDamaged(dmg, mgp); } catch (Exception ignored) {}
            // Emit attack event
            EventBus.emit(
                    GameEventType.PIECE_ATTACKED,
                    Map.of(
                            "attackerId", mgp.getId().toString(),
                            "defenderId", enemy.getId().toString(),
                            "row", dr,
                            "col", dc,
                            "attackerRow", sr,
                            "attackerCol", sc,
                            "defenderRow", dr,
                            "defenderCol", dc,
                            "damage", dmg
                    )
            );
            if (enemy.getStats().getCurrentHealth() <= 0) {
                // Notify before removal
                try { enemy.notifyDied(); } catch (Exception ignored) {}
                removeGamePieceAtPos(dr, dc);
                EventBus.emit(
                        GameEventType.PIECE_DIED,
                        Map.of(
                                "pieceId", enemy.getId().toString(),
                                "row", dr,
                                "col", dc
                        )
                );
            }
            spendAction(mgp);
            return;
        }

        // Move branch: empty destination within reach by Speed
        int speed = mgp.getStats().getSpeed();
        java.util.List<Plot> reachable = getReachablePlots(sr, sc, speed);
        boolean ok = false;
        for (Plot p : reachable) { if (p == dst) { ok = true; break; } }
        if (!ok) return;
        if (isOccupied(dr, dc)) return; // safety
        moveGamePiece(sr, sc, dr, dc);
        mgp.updateData(GamePieceData.POSITION, new Position(this, dr, dc));
        // Ability notification for movement
        try { mgp.notifyMoved(sr, sc, dr, dc); } catch (Exception ignored) {}
        // Emit move event
        EventBus.emit(
                GameEventType.PIECE_MOVED,
                Map.of(
                        "pieceId", mgp.getId().toString(),
                        "owner", mgp.getAlignment().name(),
                        "fromRow", sr,
                        "fromCol", sc,
                        "toRow", dr,
                        "toCol", dc
                )
        );
        spendAction(mgp);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        // Update candidate move spots and selected-target highlights
        updateCandidateMoveSpots();
        updatePlotHighlights();
        Set<UUID> seen = new HashSet<>();
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Renderable renderable = board[row][col];
                renderable.render(batch, zLevel, isPaused, col*PLOT_WIDTH, row*PLOT_HEIGHT);
                GamePiece gp = gamePieces[row][col];
                if (gp != null) {
                    // When using the non-offset render, sprites may be drawn elsewhere depending on pipeline,
                    // but we still render the HP overlay here aligned to the plot.
                    renderHpOverlay(batch, zLevel, col * PLOT_WIDTH, row * PLOT_HEIGHT, gp, seen);
                }
            }
        }
        Arrays.stream(rowIdentifierSymbols).forEach(s -> {
            s.render(batch, zLevel, isPaused);
        });
        Arrays.stream(colIdentifierSymbols).forEach(s -> s.render(batch, zLevel, isPaused));
        // Remove overlays for pieces not seen this frame (e.g., died or moved off-board)
        cleanupStaleHpTexts(seen);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        // Update candidate move spots and selected-target highlights
        updateCandidateMoveSpots();
        updatePlotHighlights();
        Set<UUID> seen = new HashSet<>();
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Renderable renderable = board[row][col];
                int absX = x + col * (PLOT_WIDTH);
                int absY = y + row * (PLOT_HEIGHT);
                renderable.render(batch, zLevel, isPaused, absX, absY);
                GamePiece gp = gamePieces[row][col];
                if (gp != null) {
                    gp.getSprite().render(batch, zLevel, isPaused, absX, absY);
                    renderHpOverlay(batch, zLevel, absX, absY, gp, seen);
                }
            }
        }
        Arrays.stream(rowIdentifierSymbols).forEach(s -> {
            s.render(batch, zLevel, isPaused, x + s.getX(), y + s.getY());
        });
        Arrays.stream(colIdentifierSymbols).forEach(s -> {
            s.render(batch, zLevel, isPaused, x + s.getX(), y + s.getY());
        });
        cleanupStaleHpTexts(seen);
    }
}
