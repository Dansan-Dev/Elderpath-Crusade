package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.data_objects.GamePiece;
import io.github.forest_of_dreams.data_objects.MonsterGamePiece;
import io.github.forest_of_dreams.enums.GRID_DIRECTION;
import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.ui_objects.BoardIdentifierSymbol;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.stream.IntStream;

public class Board extends HigherOrderTexture {
    private final int ROWS;
    private final int COLS;
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
        for(int row = 0; row < 7; row++) {
            for(int col = 0; col < 5; col++) {
                Plot plot = new Plot(0, 0, PLOT_WIDTH, PLOT_HEIGHT);
                int r = row;
                int c = col;
                plot.setClickableEffect(
                    (e) -> {
                        System.out.println("Clicked on plot");
                        GamePiece gp = gamePieces[r][c];
                        if (gp instanceof MonsterGamePiece mgp) {
                            mgp.moveUpOne();
                        }
                    },
                    ClickableEffectData.getImmediate()
                );
                replacePlotAtPos(row, col, plot);
            }
        }
    }

    public int[] getPixelSize() {
        return new int[]{PLOT_WIDTH*COLS, PLOT_HEIGHT*ROWS};
    }

    private char toLetter(int n) {
        if (n < 0 || n > 25) throw new IllegalArgumentException("n must be in range [0, 25]");
        return (char) ('A' + n);
    }

    private void checkBoardPosition(int row, int col) {
        if (row < 0 || row > ROWS || col < 0 || col > COLS) throw new IllegalArgumentException("row and col must be in range [0, 5] and [0, 6]");
    }

    public void setBoardIdentifierSymbols() {
        IntStream.iterate(0, i -> i + 1).limit(ROWS)
            .forEach(i -> rowIdentifierSymbols[i] = new BoardIdentifierSymbol(String.valueOf(toLetter(i)), getX()-PLOT_WIDTH/4, getY()+PLOT_HEIGHT/2+PLOT_HEIGHT*i, GRID_DIRECTION.ROW, true));
        IntStream.iterate(0, i -> i + 1).limit(COLS)
            .forEach(i -> colIdentifierSymbols[i] = new BoardIdentifierSymbol(String.valueOf(i+1), getX()+(PLOT_WIDTH)/2+PLOT_WIDTH*i, getY()-PLOT_HEIGHT/4, GRID_DIRECTION.COLUMN, true));
    }

    public Renderable getPlotAtPos(int row, int col) {
        return board[row][col];
    }

    public GamePiece getGamePieceAtPos(int row, int col) {
        return gamePieces[row][col];
    }

    public void removePos(int row, int col) {
        Renderable renderable = board[row][col];
        getRenderables().remove(renderable);
        board[row][col] = EmptyTexture.get(PLOT_WIDTH*col, PLOT_HEIGHT*row, PLOT_WIDTH, PLOT_HEIGHT);
    }

    public void setGamePiecePos(int row, int col, GamePiece gamePiece) {
        checkBoardPosition(row, col);
        gamePieces[row][col] = gamePiece;
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

        // If this is a Plot, bind its click to the GamePiece occupying this cell
        if (newRenderable instanceof Plot plot) {
            plot.setClickableEffect(
                (e) -> {
                    GamePiece gp = gamePieces[row][col];
                    if (gp instanceof MonsterGamePiece mgp) mgp.moveUpOne();
                },
                ClickableEffectData.getImmediate()
            );
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Renderable renderable = board[row][col];
                renderable.render(batch, zLevel, isPaused, col*PLOT_WIDTH, row*PLOT_HEIGHT);
            }
        }
        Arrays.stream(rowIdentifierSymbols).forEach(s -> s.render(batch, zLevel, isPaused));
        Arrays.stream(colIdentifierSymbols).forEach(s -> s.render(batch, zLevel, isPaused));
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Renderable renderable = board[row][col];
                renderable.render(batch, zLevel, isPaused, x + col*(PLOT_WIDTH), y + row*(PLOT_HEIGHT));
                if (gamePieces[row][col] != null)
                    gamePieces[row][col].getSprite().render(batch, zLevel, isPaused, x + col*PLOT_WIDTH, y + row*PLOT_HEIGHT);
            }
        }
        Arrays.stream(rowIdentifierSymbols).forEach(s -> s.render(batch, zLevel, isPaused));
        Arrays.stream(colIdentifierSymbols).forEach(s -> s.render(batch, zLevel, isPaused));
    }
}
