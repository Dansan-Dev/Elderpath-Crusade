package io.github.forest_of_dreams.game_objects.board;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.enums.ClickableTargetType;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.managers.InteractionManager;
import io.github.forest_of_dreams.managers.ZIndexRegistry;
import io.github.forest_of_dreams.utils.ColorSettings;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.GRID_DIRECTION;
import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.ui_objects.BoardIdentifierSymbol;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
        if (mgp.getAlignment() != PieceAlignment.P1) return;
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
    public java.util.List<Plot> getReachablePlots(int row, int col, int speed) {
        java.util.List<Plot> out = new java.util.ArrayList<>();
        if (speed <= 0) return out;
        boolean[][] visited = new boolean[ROWS][COLS];
        int[][] dist = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) java.util.Arrays.fill(dist[r], -1);
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
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
        List<Plot> out = new java.util.ArrayList<>();
        int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS) continue;
            GamePiece gp = getGamePieceAtPos(nr, nc);
            if (gp instanceof MonsterGamePiece mgp) {
                if (mgp.getAlignment() == PieceAlignment.P2 && friendlyAlignment == PieceAlignment.P1) {
                    Renderable r = board[nr][nc];
                    if (r instanceof Plot p) out.add(p);
                }
                // Future: handle opposite case if playing as P2, etc.
            }
        }
        return out;
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
        if (mgp.getAlignment() != PieceAlignment.P1) return;
        // Must have actions remaining
        if (getRemainingActions(mgp) <= 0) return;

        // Attack branch: adjacent hostile in 4-dir
        GamePiece targetPiece = getGamePieceAtPos(dr, dc);
        int manhattan = Math.abs(dr - sr) + Math.abs(dc - sc);
        if (targetPiece instanceof MonsterGamePiece enemy && enemy.getAlignment() == PieceAlignment.P2 && manhattan == 1) {
            enemy.getStats().dealDamage(mgp.getStats().getDamage());
            if (enemy.getStats().getCurrentHealth() <= 0) {
                removeGamePieceAtPos(dr, dc);
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
        spendAction(mgp);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        // Update candidate move spots and selected-target highlights
        updateCandidateMoveSpots();
        updatePlotHighlights();
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Renderable renderable = board[row][col];
                renderable.render(batch, zLevel, isPaused, col*PLOT_WIDTH, row*PLOT_HEIGHT);
            }
        }
        Arrays.stream(rowIdentifierSymbols).forEach(s -> {
            s.render(batch, zLevel, isPaused);
        });
        Arrays.stream(colIdentifierSymbols).forEach(s -> s.render(batch, zLevel, isPaused));
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        // Update candidate move spots and selected-target highlights
        updateCandidateMoveSpots();
        updatePlotHighlights();
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Renderable renderable = board[row][col];
                renderable.render(batch, zLevel, isPaused, x + col*(PLOT_WIDTH), y + row*(PLOT_HEIGHT));
                if (gamePieces[row][col] != null)
                    gamePieces[row][col].getSprite().render(batch, zLevel, isPaused, x + col*PLOT_WIDTH, y + row*PLOT_HEIGHT);
            }
        }
        Arrays.stream(rowIdentifierSymbols).forEach(s -> {
            s.render(batch, zLevel, isPaused, x + s.getX(), y + s.getY());
        });
        Arrays.stream(colIdentifierSymbols).forEach(s -> {
            s.render(batch, zLevel, isPaused, x + s.getX(), y + s.getY());
        });
    }
}
