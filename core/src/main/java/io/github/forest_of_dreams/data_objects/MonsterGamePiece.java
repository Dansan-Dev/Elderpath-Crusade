package io.github.forest_of_dreams.data_objects;
import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.settings.GamePieceType;
import io.github.forest_of_dreams.game_objects.Board;
import io.github.forest_of_dreams.interfaces.Renderable;
import java.util.Optional;
import java.util.UUID;

public class MonsterGamePiece extends GamePiece {

    private record BoardContext(Board board, Board.Position position) {}

    public MonsterGamePiece(GamePieceStats stats, GamePieceType type, PieceAlignment alignment, UUID id, Renderable sprite) {
        super(stats, type, alignment, id, sprite);
        if (type.equals(GamePieceType.TERRAIN)) throw new IllegalArgumentException("Cannot create a monster as terrain");
    }

    // Generic interaction triggered by Plot: move this piece one step upwards if possible
    public void moveUpOne() {
        Optional<BoardContext> context = getBoardContext();
        if (context.isEmpty()) return;

        BoardContext ctx = context.get();
        Board.Position pos = ctx.position;
        Board board = ctx.board;
        int currentRow = pos.getRow();
        int currentCol = pos.getCol();

        boolean isMovablePiece = !type.equals(GamePieceType.TERRAIN) && alignment.equals(PieceAlignment.ALLIED);
        if (!isMovablePiece) return;

        int newRow = currentRow + 1; // upwards
        boolean isValidDestination = ctx.position.isValid(newRow, currentCol);
        if (!isValidDestination) return;

        GamePiece gamePiece = board.getGamePieceAtPos(newRow, currentCol);
        if (gamePiece != null && gamePiece.type.equals(GamePieceType.TERRAIN)) return;
        else if (board.getGamePieceAtPos(newRow, currentCol) instanceof MonsterGamePiece mgp) attack();
        else {
            board.moveGamePiece(currentRow, currentCol, newRow, currentCol);
            pos.setRow(newRow);
        }
    }

    public void attack() {
        Optional<BoardContext> context = getBoardContext();
        if (context.isEmpty()) return;

        BoardContext ctx = context.get();
        Board.Position pos = ctx.position;
        Board board = ctx.board;
        int currentRow = ctx.position.getRow();
        int currentCol = ctx.position.getCol();

        int newRow = currentRow + 1;
        if (!pos.isValid(newRow, currentCol)) return;
        if (!(board.getGamePieceAtPos(newRow, currentCol) instanceof MonsterGamePiece mgp)) return;
        mgp.stats.dealDamage(stats.getDamage());
        if (mgp.stats.getCurrentHealth()<=0) mgp.die();
    }

    public void die() {
        Optional<BoardContext> context = getBoardContext();
        if (context.isEmpty()) return;

        BoardContext ctx = context.get();
        Board.Position pos = ctx.position;
        Board board = ctx.board;
        board.removeGamePieceAtPos(pos.getRow(), pos.getCol());
    }

    private Optional<BoardContext> getBoardContext() {
        Object posObj = getData(GamePieceData.POSITION);
        if (!(posObj instanceof Board.Position pos)) {
            return Optional.empty();
        }

        Board board = pos.getBoard();
        if (board == null) {
            return Optional.empty();
        }

        return Optional.of(new BoardContext(board, pos));
    }
}
