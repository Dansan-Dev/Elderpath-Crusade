package io.github.elderpath_crusade.game_objects.board;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.enums.*;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.EmptyTexture;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.Updatable;
import io.github.elderpath_crusade.rendering.ZIndexRegistry;
import io.github.elderpath_crusade.game.TurnManager;
import io.github.elderpath_crusade.model.board.BoardModel;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.supers.HigherOrderTexture;
import io.github.elderpath_crusade.ui_objects.BoardIdentifierSymbol;
import io.github.elderpath_crusade.game_objects.board.components.BoardInteractionResolver;
import io.github.elderpath_crusade.game_objects.board.components.BoardNavigator;
import io.github.elderpath_crusade.game_objects.board.components.BoardOverlayRenderer;
import io.github.elderpath_crusade.game_objects.board.components.BoardPerspectiveManager;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.game_objects.board.components.BoardRenderer;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.IntStream;

public class Board extends HigherOrderTexture implements Updatable {
    @Getter private final int ROWS;
    @Getter private final int COLS;
    @Getter private final int PLOT_WIDTH;
    @Getter private final int PLOT_HEIGHT;
    @Getter private final Renderable[][] layout;
    @Getter private final BoardIdentifierSymbol[] rowIdentifierSymbols;
    @Getter private final BoardIdentifierSymbol[] colIdentifierSymbols;

    private final BoardPerspectiveManager perspectiveManager;
    private final BoardNavigator navigator;
    private final BoardInteractionResolver interactionResolver;
    private final BoardOverlayRenderer overlayRenderer;
    private final BoardRenderer boardRenderer;
    @Getter private final BoardModel model;

    private GridIndexSystem gridIndex;

    private GridIndexSystem getGridIndex() {
        if (gridIndex == null) {
            gridIndex = GameContext.get().getEcsEngine().getSystem(GridIndexSystem.class);
        }
        return gridIndex;
    }

    @Override
    public void update(float delta) {
    }



    private List<Integer> cachedZs = new ArrayList<>();
    private boolean zsDirty = true;

    public Board(int x, int y, int plot_width, int plot_height, int rows, int cols) {
        ROWS = rows;
        COLS = cols;
        PLOT_WIDTH = plot_width;
        PLOT_HEIGHT = plot_height;
        rowIdentifierSymbols = new BoardIdentifierSymbol[ROWS];
        colIdentifierSymbols = new BoardIdentifierSymbol[COLS];
        layout = new Renderable[ROWS][COLS];
        model = new BoardModel(rows, cols);
        perspectiveManager = new BoardPerspectiveManager(this);
        navigator = new BoardNavigator(this);
        interactionResolver = new BoardInteractionResolver(this);
        overlayRenderer = new BoardOverlayRenderer(this);
        boardRenderer = new BoardRenderer(this, overlayRenderer);
        setBounds(new Box(x, y, PLOT_WIDTH * COLS, PLOT_HEIGHT * ROWS));

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Renderable renderable = EmptyTexture.get(PLOT_WIDTH * col, PLOT_HEIGHT * row, PLOT_WIDTH, PLOT_HEIGHT);
                layout[row][col] = renderable;
            }
        }
        setBoardIdentifierSymbols();

        TypedEventBus.get().registerScoped("session", TurnStartedEvent.class, this::onTurnStarted);
        TypedEventBus.get().registerScoped("session", TurnEndedEvent.class, this::onTurnEnded);
    }

    private void onTurnStarted(TurnStartedEvent event) {
        if (GameContext.get().getGameModeManager().getCurrent() == GameMode.LOCAL_MATCH) {
            boolean shouldBeFlipped = (event.player() == PieceAlignment.P2);
            if (shouldBeFlipped != isFlipped()) { flipRows(); }
        }
    }

    private void onTurnEnded(TurnEndedEvent event) {
        // No longer needed — AbilityRelay handles turn hooks via ECS
    }


    public static class Position {
        @Getter private final Board board;
        private int row;
        private int col;
        private PositionComponent ecsPosition;

        public Position(Board board, int row, int col) {
            this.board = board;
            this.row = row;
            this.col = col;
        }

        public void linkEntity(Entity entity) {
            if (entity != null) {
                this.ecsPosition = entity.getComponent(PositionComponent.class);
            }
        }

        public int getRow() { return ecsPosition != null ? ecsPosition.row : row; }
        public int getCol() { return ecsPosition != null ? ecsPosition.col : col; }

        public void setRow(int row) {
            if (ecsPosition != null) ecsPosition.row = row;
            else this.row = row;
        }

        public void setCol(int col) {
            if (ecsPosition != null) ecsPosition.col = col;
            else this.col = col;
        }

        public boolean isValid(int row, int col) {
            return row >= 0 && row < board.getROWS() && col >= 0 && col < board.getCOLS();
        }
    }

    public void markDirtyAndNotify() {
        zsDirty = true;
        GameContext.get().getZIndexRegistry().notifyZChanged(this);
    }

    public Plot getPlotAtScreen(int mouseX, int mouseY) {
        int[] pos = calculatePos();
        int localX = mouseX - pos[0];
        int localY = mouseY - pos[1];
        int col = localX / PLOT_WIDTH;
        int row = localY / PLOT_HEIGHT;
        if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
            Renderable r = layout[row][col];
            if (r instanceof Plot p)
                return p;
        }
        return null;
    }

    public void initializePlots() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Plot plot = new Plot(0, 0, PLOT_WIDTH, PLOT_HEIGHT);
                if (row == 0)
                    plot.withPlotColor(ColorSettings.PLOT_PLAYER_1_ROW.getColor());
                if (row == ROWS - 1)
                    plot.withPlotColor(ColorSettings.PLOT_PLAYER_2_ROW.getColor());
                plot.setBoard(this);
                plot.setGridPos(row, col);
                plot.setClickableEffect(
                        this::handlePlotMove,
                        ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1));
                replacePlotAtPos(row, col, plot);
            }
        }
        markDirtyAndNotify();
    }

    public int[] getPixelSize() {
        return new int[] { PLOT_WIDTH * COLS, PLOT_HEIGHT * ROWS };
    }

    private char toLetter(int n) {
        if (n < 0 || n > 25)
            throw new IllegalArgumentException("n must be in range [0, 25]");
        return (char) ('A' + n);
    }

    private void checkBoardPosition(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            throw new IllegalArgumentException(
                    "row must be in [0, " + (ROWS - 1) + "] and col must be in [0, " + (COLS - 1) + "]");
        }
    }

    public void setBoardIdentifierSymbols() {
        IntStream.iterate(0, i -> i + 1).limit(ROWS)
                .forEach(i -> rowIdentifierSymbols[i] = new BoardIdentifierSymbol(
                        String.valueOf(toLetter(i)),
                        -PLOT_WIDTH / 4,
                        PLOT_HEIGHT / 2 + PLOT_HEIGHT * i,
                        GridDirection.ROW,
                        true));
        IntStream.iterate(0, i -> i + 1).limit(COLS)
                .forEach(i -> colIdentifierSymbols[i] = new BoardIdentifierSymbol(
                        String.valueOf(i + 1),
                        (PLOT_WIDTH) / 2 + PLOT_WIDTH * i,
                        -PLOT_HEIGHT / 4,
                        GridDirection.COLUMN,
                        true));
        markDirtyAndNotify();
    }

    public List<Plot> getAttackableEnemyPlots(int row, int col, PieceAlignment friendlyAlignment) {
        return navigator.getAttackableEnemyPlots(row, col, friendlyAlignment);
    }

    public Renderable getPlotAtPos(int row, int col) {
        return layout[row][col];
    }

    public GamePiece getGamePieceAtPos(int row, int col) {
        Entity entity = getEntityAtPos(row, col);
        if (entity == null) return null;
        io.github.elderpath_crusade.ecs.components.PieceRefComponent ref =
                entity.getComponent(io.github.elderpath_crusade.ecs.components.PieceRefComponent.class);
        return ref != null ? ref.piece : null;
    }

    public com.badlogic.ashley.core.Entity getEntityAtPos(int row, int col) {
        GridIndexSystem gi = getGridIndex();
        return gi != null ? gi.getEntityAt(row, col) : null;
    }

    public GamePiece getGamePieceAtPlot(Plot plot) {
        if (plot == null)
            return null;
        return getGamePieceAtPos(plot.getRow(), plot.getCol());
    }

    public Entity getEntityAtPlot(Plot plot) {
        if (plot == null) return null;
        return getEntityAtPos(plot.getRow(), plot.getCol());
    }

    public boolean isValidSummonTarget(Plot plot, PieceAlignment alignment) {
        if (plot == null || alignment == null)
            return false;
        int[] idx = plot.getIndices();
        if (idx == null)
            return false;
        if (getGamePieceAtPos(idx[0], idx[1]) != null)
            return false;

        boolean flipped = isFlipped();

        return switch (alignment) {
            case P1 -> flipped ? (idx[0] == ROWS - 1) : (idx[0] == 0);
            case P2 -> flipped ? (idx[0] == 0) : (idx[0] == ROWS - 1);
            default -> false;
        };
    }

    public void removePlotAtPos(int row, int col) {
        Renderable renderable = layout[row][col];
        getRenderables().remove(renderable);
        layout[row][col] = EmptyTexture.get(PLOT_WIDTH * col, PLOT_HEIGHT * row, PLOT_WIDTH, PLOT_HEIGHT);
    }

    public void removeGamePieceAtPos(int row, int col) {
        setGamePiecePos(row, col, null);
    }

    public void setGamePiecePos(int row, int col, GamePiece gamePiece) {
        checkBoardPosition(row, col);
        GridIndexSystem gi = getGridIndex();
        if (gamePiece != null) {
            if (gamePiece instanceof MonsterGamePiece mgp && mgp.getEntity() != null && gi != null) {
                gi.onEntitySpawned(mgp.getEntity(), row, col);
            }
            if (model.isOccupied(row, col)) model.removePiece(row, col);
            model.placePiece(row, col, gamePiece.getId().toString());
        } else {
            if (gi != null) gi.onEntityDied(row, col);
            if (model.isOccupied(row, col)) model.removePiece(row, col);
        }
        markDirtyAndNotify();
    }

    public void moveGamePiece(int currentRow, int currentCol, int newRow, int newCol) {
        GamePiece gamePiece = getGamePieceAtPos(currentRow, currentCol);
        GridIndexSystem gi = getGridIndex();
        if (gi != null && gamePiece instanceof MonsterGamePiece mgp && mgp.getEntity() != null) {
            gi.onEntityMoved(currentRow, currentCol, mgp.getEntity(), newRow, newCol);
        }
        // Sync BoardModel
        if (model.isOccupied(currentRow, currentCol)) model.removePiece(currentRow, currentCol);
        if (gamePiece != null) {
            if (model.isOccupied(newRow, newCol)) model.removePiece(newRow, newCol);
            model.placePiece(newRow, newCol, gamePiece.getId().toString());
        }
        markDirtyAndNotify();
    }

    public void addGamePieceToPos(int row, int col, GamePiece gamePiece) {
        setGamePiecePos(row, col, gamePiece);
        gamePiece.updateData(GamePieceData.POSITION, new Position(this, row, col));
        if (gamePiece instanceof MonsterGamePiece mgp) {
            mgp.getStats().setRemainingActions(0);
            mgp.notifySpawned(row, col);
        }
        io.github.elderpath_crusade.events.TypedEventBus.get().emit(
                new io.github.elderpath_crusade.events.PieceSpawnedEvent(
                        gamePiece.getId().toString(),
                        gamePiece.getAlignment(),
                        row, col));
    }

    private void replacePlotAtPos(int row, int col, Renderable newRenderable) {
        if (newRenderable.getBounds().getWidth() != PLOT_WIDTH
                || newRenderable.getBounds().getHeight() != PLOT_HEIGHT)
            throw new IllegalArgumentException("Renderable must be in PLOT size");

        Renderable renderable = layout[row][col];
        getRenderables().remove(renderable);

        if (newRenderable.getBounds() != null) {
            newRenderable.getBounds().setX(col * PLOT_WIDTH);
            newRenderable.getBounds().setY(row * PLOT_HEIGHT);
        }
        newRenderable.setParent(getBounds());
        layout[row][col] = newRenderable;
        getRenderables().add(newRenderable);

        if (newRenderable instanceof Plot plot) {
            plot.setBoard(this);
            plot.setClickableEffect(
                    this::handlePlotMove,
                    ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1));
        }
        markDirtyAndNotify();
    }

    public boolean isOccupied(int row, int col) {
        return getGamePieceAtPos(row, col) != null;
    }

    public List<Plot> getReachablePlots(int row, int col, int speed) {
        return navigator.getReachablePlots(row, col, speed);
    }

    public List<Plot> getAdjacentHostilePlots(int row, int col, PieceAlignment friendlyAlignment) {
        return navigator.getAdjacentHostilePlots(row, col, friendlyAlignment);
    }


    public void handlePlotMove(HashMap<Integer, CustomBox> entities) {
        interactionResolver.handlePlotMove(entities);
    }

    public PieceAlignment getCurrentPlayer() {
        return GameContext.get().getTurnManager().getCurrentPlayer();
    }

    public boolean isFlipped() {
        return perspectiveManager.isPhysicallyFlipped();
    }

    @Override
    public List<Integer> getZs() {
        if (zsDirty) {
            Set<Integer> zSet = new HashSet<>();
            for (Renderable r : getRenderables()) {
                zSet.addAll(r.getZs());
            }
            ImmutableArray<Entity> entities = GameContext.get().getEcsEngine()
                    .getEntitiesFor(Family.all(SpriteComponent.class).get());
            for (int i = 0; i < entities.size(); i++) {
                SpriteComponent sc = entities.get(i).getComponent(SpriteComponent.class);
                if (sc != null && sc.renderable != null) {
                    List<Integer> pZs = sc.renderable.getZs();
                    zSet.addAll(pZs);
                    for (Integer pz : pZs) {
                        zSet.add(pz + 3);
                    }
                }
            }
            for (BoardIdentifierSymbol s : rowIdentifierSymbols) {
                if (s != null)
                    zSet.addAll(s.getZs());
            }
            for (BoardIdentifierSymbol s : colIdentifierSymbols) {
                if (s != null)
                    zSet.addAll(s.getZs());
            }

            cachedZs = new ArrayList<>(zSet);
            Collections.sort(cachedZs);
            zsDirty = false;
        }
        return cachedZs;
    }

    public void flipRows() {
        perspectiveManager.flipRows();
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        boardRenderer.render(batch, zLevel, isPaused);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        boardRenderer.render(batch, zLevel, isPaused, x, y);
    }
}
