package io.github.elderpath_crusade.game_objects.board;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.abilities.BasicAbility;
import io.github.elderpath_crusade.abilities.impl._base.BaseAttackAbility;
import io.github.elderpath_crusade.abilities.impl._base.BaseMoveAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.JumpMoveAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.OncePerTurnAttackAbility;
import io.github.elderpath_crusade.enums.*;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.managers.InteractionManager;
import io.github.elderpath_crusade.managers.ZIndexRegistry;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.supers.HigherOrderTexture;
import io.github.elderpath_crusade.ui_objects.BoardIdentifierSymbol;
import io.github.elderpath_crusade.utils.GraphicUtils;
import io.github.elderpath_crusade.path_loaders.ImagePathSpritesAndAnimations;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.Gdx;
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
    @Getter private final GamePiece [][] gamePieces;
    private final BoardIdentifierSymbol[] rowIdentifierSymbols;
    private final BoardIdentifierSymbol[] colIdentifierSymbols;
    // Track physical flip state (true = board is flipped for P2's perspective)
    private boolean physicallyFlipped = false;

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

    // Stun symbol texture cache
    private static Texture stunTexture = null;
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

    // --- Status effect visual rendering ---
    /**
     * Render a piece sprite with status effect tinting (stun or exhaustion).
     * Priority: Stun (purple/pink/blue tint) > Exhaustion (darkening).
     * Exhaustion is only shown for the current player's pieces.
     */
    private void renderPieceWithStatusEffects(SpriteBatch batch, int zLevel, int absX, int absY, GamePiece gp) {
        if (!(gp instanceof MonsterGamePiece mgp)) {
            // Non-monster pieces render normally without effects
            gp.getSprite().render(batch, zLevel, false, absX, absY);
            return;
        }

        // Save current batch color
        Color originalColor = batch.getColor().cpy();

        // Determine status effect and apply appropriate tint
        if (mgp.isStunned()) {
            // Stun: more purple/pink tint (apply color tint to sprite)
            // More purple/pink: higher red and blue, lower green
            Color stunTint = new Color(1f, 0.22f, 0.71f, 1f);
            batch.setColor(stunTint);
            gp.getSprite().render(batch, zLevel, false, absX, absY);
            batch.setColor(originalColor);

            // Render stun symbol overlay on top of the piece
            renderStunSymbol(batch, zLevel, absX, absY);
        } else if (mgp.isExhausted()) {
            // Exhaustion: darkening effect (only show for current player's pieces)
            PieceAlignment currentPlayer = TurnManager.getCurrentPlayer();
            if (mgp.getAlignment() == currentPlayer) {
                // Multiply RGB by 0.6 to darken, keep alpha at 1.0
                Color darkenTint = new Color(0.6f, 0.6f, 0.6f, 1.0f);
                batch.setColor(darkenTint);
                gp.getSprite().render(batch, zLevel, false, absX, absY);
                batch.setColor(originalColor);
            } else {
                // Not current player's piece: render normally
                gp.getSprite().render(batch, zLevel, false, absX, absY);
            }
        } else {
            // No status effect: render normally
            gp.getSprite().render(batch, zLevel, false, absX, absY);
        }
    }

    // --- Stun symbol overlay rendering ---
    /**
     * Render the stun symbol overlay on top of a stunned piece.
     * The symbol is centered on the plot and sized appropriately.
     */
    private void renderStunSymbol(SpriteBatch batch, int zLevel, int absX, int absY) {
        // Load stun texture if not already loaded
        if (stunTexture == null) {
            try {
                stunTexture = new Texture(Gdx.files.internal(ImagePathSpritesAndAnimations.STUN.getPath()));
            } catch (Exception e) {
                // If texture loading fails, just skip rendering the symbol
                return;
            }
        }

        // Render stun symbol centered on the plot, sized to ~60% of plot size
        int symbolSize = Math.min(PLOT_WIDTH, PLOT_HEIGHT) * 3 / 5; // 60% of smaller dimension
        int symbolX = absX + (PLOT_WIDTH - symbolSize) / 2;
        int symbolY = absY + (PLOT_HEIGHT - symbolSize) / 2;

        // Render the stun symbol at the same z-level as the piece (will appear on top due to draw order)
        batch.draw(stunTexture, symbolX, symbolY, symbolSize, symbolSize);
    }

    // --- Compact health overlay helpers ---
    private void renderHpOverlay(SpriteBatch batch, int zLevel, int absX, int absY, GamePiece gp, Set<UUID> seen) {
        if (!(gp instanceof MonsterGamePiece mgp)) return;
        GamePieceStats st = mgp.getStats();
        int cur = st.getCurrentHealth();
        int max = mgp.getEffectiveMaxHealth(); // Use effective max health (includes modifiers)
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

    public static class Position {
        @Getter private final Board board;
        @Getter @Setter private int row;
        @Getter @Setter private int col;

        public Position(Board board, int row, int col) {
            this.board = board;
            this.row = row;
            this.col = col;
        }

        public boolean isValid(int row, int col) {
            return row >= 0 && row < board.getROWS() && col >= 0 && col < board.getCOLS();
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
                GridDirection.ROW,
                true
            ));
        IntStream.iterate(0, i -> i + 1).limit(COLS)
            .forEach(i -> colIdentifierSymbols[i] = new BoardIdentifierSymbol(
                String.valueOf(i+1),
                (PLOT_WIDTH)/2+PLOT_WIDTH*i,
                -PLOT_HEIGHT/4,
                GridDirection.COLUMN,
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

    // Return enemy plots attackable from (row,col) for a given alignment, using cardinal lines with blockers and range.
    public List<Plot> getAttackableEnemyPlots(int row, int col, PieceAlignment friendlyAlignment) {
        List<Plot> out = new ArrayList<>();
        GamePiece src = getGamePieceAtPos(row, col);
        if (!(src instanceof MonsterGamePiece attacker)) return out;
        int effRange = attacker.getEffectiveRange();
        if (effRange < 0) return out; // Cannot attack if range is negative
        effRange = Math.max(1, effRange);
        int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
        boolean ignoreTerrain = attacker.ignoresTerrainAsBlockers();
        boolean ignoreFriendly = attacker.ignoresFriendlyUnitsAsBlockers();
        boolean ignoreHostile = attacker.ignoresHostileUnitsAsBlockers();
        for (int[] d : dirs) {
            for (int dist = 1; dist <= effRange; dist++) {
                int nr = row + d[0] * dist;
                int nc = col + d[1] * dist;
                if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS) break;
                GamePiece gp = getGamePieceAtPos(nr, nc);
                if (gp != null) {
                    boolean isTerrain = gp.getType() == GamePieceType.TERRAIN;
                    boolean isUnit = gp instanceof MonsterGamePiece;
                    boolean hostile = isUnit && ((MonsterGamePiece) gp).getAlignment() != friendlyAlignment;
                    boolean friendly = isUnit && ((MonsterGamePiece) gp).getAlignment() == friendlyAlignment;
                    boolean blockedByTerrain = isTerrain && !ignoreTerrain;
                    boolean blockedByFriendly = friendly && !ignoreFriendly;
                    boolean blockedByHostile = hostile && !ignoreHostile;
                    // If hostile is in line, it is a valid target (even if we will continue scanning when ignoreHostile=true)
                    if (hostile) {
                        Renderable r = board[nr][nc];
                        if (r instanceof Plot p) out.add(p);
                    }
                    // Stop scanning if blocked by this entity; otherwise continue past it
                    if (blockedByTerrain || blockedByFriendly || blockedByHostile || (!isTerrain && !isUnit)) {
                        break;
                    } else {
                        continue;
                    }
                }
                // Empty tile: continue scanning
            }
        }
        return out;
    }

    // Mark candidate move plots (white dots) and attack plots (red glow) when a movement source is active
    private void updateCandidateMoveSpots() {
        Object src = InteractionManager.getActiveSource();
        // Skip if an ability interaction is active (let InteractionManager.renderEligibleTargets() handle highlighting)
        if (src != null && !(src instanceof Plot)) return;
        // Clear all by default
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Renderable r = board[row][col];
                if (r instanceof Plot p) {
                    p.setCandidate(false);
                    p.setAttackCandidate(false);
                    p.setFriendlyCandidate(false);
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
        // Stunned pieces cannot act - don't show candidate dots
        if (mgp.isStunned()) return;

        // Use BasicAbility (BaseMoveAbility, JumpMoveAbility, BaseAttackAbility, and OncePerTurnAttackAbility) to get eligible targets
        // Prioritize JumpMoveAbility over BaseMoveAbility if both exist
        List<Plot> reachable = List.of();
        List<Plot> attackables = List.of();
        JumpMoveAbility jumpMoveAbility = null;
        BaseMoveAbility baseMoveAbility = null;
        OncePerTurnAttackAbility oncePerTurnAttackAbility = null;
        for (Ability ability : mgp.getAbilities()) {
            if (ability instanceof BasicAbility basicAbility) {
                if (basicAbility instanceof JumpMoveAbility) {
                    jumpMoveAbility = (JumpMoveAbility) basicAbility;
                } else if (basicAbility instanceof BaseMoveAbility) {
                    baseMoveAbility = (BaseMoveAbility) basicAbility;
                } else if (basicAbility instanceof OncePerTurnAttackAbility) {
                    oncePerTurnAttackAbility = (OncePerTurnAttackAbility) basicAbility;
                } else if (basicAbility instanceof BaseAttackAbility) {
                    // Only use BaseAttackAbility if OncePerTurnAttackAbility is not present
                    if (oncePerTurnAttackAbility == null) {
                        attackables = basicAbility.getEligibleTargets(1);
                    }
                }
            }
        }
        // Use OncePerTurnAttackAbility if available, otherwise BaseAttackAbility
        if (oncePerTurnAttackAbility != null) {
            attackables = oncePerTurnAttackAbility.getEligibleTargets(1);
        }
        // Use JumpMoveAbility if available, otherwise use BaseMoveAbility
        if (jumpMoveAbility != null) {
            reachable = jumpMoveAbility.getEligibleTargets(1);
        } else if (baseMoveAbility != null) {
            reachable = baseMoveAbility.getEligibleTargets(1);
        }
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
     * appropriate summon row for the given alignment.
     * Accounts for board flip state in LOCAL_MATCH mode:
     * - When not flipped (P1's turn): P1 → row 0, P2 → row ROWS-1
     * - When flipped (P2's turn): P1 → row ROWS-1, P2 → row 0
     */
    public boolean isValidSummonTarget(Plot plot, io.github.elderpath_crusade.enums.PieceAlignment alignment) {
        if (plot == null || alignment == null) return false;
        int[] idx = getIndicesOfPlot(plot);
        if (idx == null) return false;
        // must be empty
        if (getGamePieceAtPos(idx[0], idx[1]) != null) return false;

        // Check if board is currently flipped (for P2's turn in LOCAL_MATCH)
        boolean flipped = isFlipped();

        // row policy: account for board flip state
        switch (alignment) {
            case P1:
                // When flipped, P1's home row is at ROWS-1; when not flipped, it's at 0
                return flipped ? (idx[0] == ROWS - 1) : (idx[0] == 0);
            case P2:
                // When flipped, P2's home row is at 0; when not flipped, it's at ROWS-1
                return flipped ? (idx[0] == 0) : (idx[0] == ROWS - 1);
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
        // Apply summoning sickness: pieces start with 0 remaining actions unless an ability overrides
        if (gamePiece instanceof MonsterGamePiece mgp) {
            mgp.getStats().setRemainingActions(0);
            // Notify abilities on spawn (abilities can override summoning sickness by setting remainingActions)
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
        // Use effective damage (base + passive modifiers)
        return attacker.getEffectiveDamage();
    }

    public void resetActionsForOwner(PieceAlignment owner) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                GamePiece gp = gamePieces[r][c];
                if (gp instanceof MonsterGamePiece mgp && mgp.getAlignment() == owner) {
                    // Reset actions normally (stun doesn't affect remainingActions, it only blocks execution)
                    mgp.getStats().setRemainingActions(mgp.getEffectiveActions());
                }
            }
        }
    }

    private int getRemainingActions(MonsterGamePiece mgp) {
        return mgp.getStats().getRemainingActions();
    }

    private void spendAction(MonsterGamePiece mgp) {
        int left = Math.max(0, getRemainingActions(mgp) - 1);
        mgp.getStats().setRemainingActions(left);
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
        // Resolve source and destination to Plots (accept GamePiece as well)
        Plot src = null; Plot dst = null;
        if (s instanceof Plot sp) src = sp;
        else if (s instanceof GamePiece sgp) {
            Object posObj = sgp.getData(GamePieceData.POSITION);
            if (posObj instanceof Position pos && pos.getBoard() == this) {
                Renderable r = getPlotAtPos(pos.getRow(), pos.getCol());
                if (r instanceof Plot p) src = p;
            }
        }
        if (t instanceof Plot tp) dst = tp;
        else if (t instanceof GamePiece tgp) {
            Object posObj = tgp.getData(GamePieceData.POSITION);
            if (posObj instanceof Position pos && pos.getBoard() == this) {
                Renderable r = getPlotAtPos(pos.getRow(), pos.getCol());
                if (r instanceof Plot p) dst = p;
            }
        }
        if (src == null || dst == null) return;
        int[] sIdx = getIndicesOfPlot(src);
        int[] dIdx = getIndicesOfPlot(dst);
        if (sIdx == null || dIdx == null) return;
        int sr = sIdx[0], sc = sIdx[1];
        int dr = dIdx[0], dc = dIdx[1];
        GamePiece gp = getGamePieceAtPos(sr, sc);
        if (!(gp instanceof MonsterGamePiece mgp)) return;
        if (mgp.getAlignment() != TurnManager.getCurrentPlayer()) return;
        // Stunned pieces cannot act (even if they have remaining actions)
        if (mgp.isStunned()) return;
        // Must have actions remaining
        if (getRemainingActions(mgp) <= 0) return;

        // Build entities map for ability execution (0=source, 1=target)
        HashMap<Integer, CustomBox> abilityEntities = new HashMap<>();
        abilityEntities.put(0, src);
        abilityEntities.put(1, dst);

        // Check if there's an enemy at destination -> try attack ability
        GamePiece targetPiece = getGamePieceAtPos(dr, dc);
        if (targetPiece instanceof MonsterGamePiece enemy && enemy.getAlignment() != mgp.getAlignment()) {
            // Find BasicAbility that is an attack ability (prioritize OncePerTurnAttackAbility)
            OncePerTurnAttackAbility oncePerTurnAttackAbility = null;
            BaseAttackAbility baseAttackAbility = null;
            for (Ability ability : mgp.getAbilities()) {
                if (ability instanceof OncePerTurnAttackAbility) {
                    oncePerTurnAttackAbility = (OncePerTurnAttackAbility) ability;
                } else if (ability instanceof BaseAttackAbility) {
                    baseAttackAbility = (BaseAttackAbility) ability;
                }
            }
            // Try OncePerTurnAttackAbility first if available
            if (oncePerTurnAttackAbility != null) {
                if (oncePerTurnAttackAbility.isValidTargetForEffect(dst, 1)) {
                    oncePerTurnAttackAbility.execute(abilityEntities);
                    return;
                }
            }
            // Otherwise try BaseAttackAbility
            if (baseAttackAbility != null) {
                if (baseAttackAbility.isValidTargetForEffect(dst, 1)) {
                    baseAttackAbility.execute(abilityEntities);
                    return;
                }
            }
            return; // No valid attack ability found
        }

        // Otherwise, try move ability
        // Prioritize JumpMoveAbility over BaseMoveAbility if both exist
        JumpMoveAbility jumpMoveAbility = null;
        BaseMoveAbility baseMoveAbility = null;
        for (Ability ability : mgp.getAbilities()) {
            if (ability instanceof JumpMoveAbility) {
                jumpMoveAbility = (JumpMoveAbility) ability;
            } else if (ability instanceof BaseMoveAbility) {
                baseMoveAbility = (BaseMoveAbility) ability;
            }
        }
        // Try JumpMoveAbility first if available
        if (jumpMoveAbility != null) {
            if (jumpMoveAbility.isValidTargetForEffect(dst, 1)) {
                jumpMoveAbility.execute(abilityEntities);
                return;
            }
        }
        // Otherwise try BaseMoveAbility
        if (baseMoveAbility != null) {
            if (baseMoveAbility.isValidTargetForEffect(dst, 1)) {
                baseMoveAbility.execute(abilityEntities);
                return;
            }
        }
        // No valid move ability found
    }

    /**
     * Check if board is currently flipped (row mirroring) for P2's perspective in LOCAL_MATCH mode.
     * Uses the tracked physical flip state.
     */
    public boolean isFlipped() {
        return physicallyFlipped;
    }

    /**
     * Physically flip the board by swapping rows in the board and gamePieces arrays.
     * Row 0 swaps with row (ROWS-1), row 1 swaps with row (ROWS-2), etc.
     * Updates all plot bounds and GamePiece POSITION data to reflect new positions.
     */
    public void flipRows() {
        // Swap plots and game pieces in the arrays
        for (int row = 0; row < ROWS / 2; row++) {
            int swapRow = ROWS - 1 - row;

            // Swap plots in board array
            Renderable[] tempRow = board[row];
            board[row] = board[swapRow];
            board[swapRow] = tempRow;

            // Swap game pieces in gamePieces array
            GamePiece[] tempPieces = gamePieces[row];
            gamePieces[row] = gamePieces[swapRow];
            gamePieces[swapRow] = tempPieces;

            // Update bounds for swapped plots in both rows
            for (int col = 0; col < COLS; col++) {
                // Update plot bounds to match new row positions
                Renderable plot = board[row][col];
                if (plot != null && plot.getBounds() != null) {
                    plot.getBounds().setX(col * PLOT_WIDTH);
                    plot.getBounds().setY(row * PLOT_HEIGHT);
                }

                Renderable swapPlot = board[swapRow][col];
                if (swapPlot != null && swapPlot.getBounds() != null) {
                    swapPlot.getBounds().setX(col * PLOT_WIDTH);
                    swapPlot.getBounds().setY(swapRow * PLOT_HEIGHT);
                }
            }
        }

        // Update all game pieces' POSITION data to reflect new row positions
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                GamePiece gp = gamePieces[row][col];
                if (gp != null) {
                    gp.updateData(GamePieceData.POSITION, new Position(this, row, col));
                }
            }
        }

        // Toggle the tracked flip state
        physicallyFlipped = !physicallyFlipped;

        // Notify z-index registry that board structure changed
        ZIndexRegistry.notifyZChanged(this);
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
                    // Render piece sprite with status effect tinting
                    renderPieceWithStatusEffects(batch, zLevel, absX, absY, gp);
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
