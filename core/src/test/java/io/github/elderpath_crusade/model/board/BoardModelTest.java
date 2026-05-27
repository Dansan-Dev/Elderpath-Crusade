package io.github.elderpath_crusade.model.board;

import io.github.elderpath_crusade.enums.PieceAlignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardModelTest {

    private BoardModel board;

    @BeforeEach
    void setUp() {
        board = new BoardModel(7, 5);
    }

    @Test
    void constructorSetsSize() {
        assertEquals(7, board.getRows());
        assertEquals(5, board.getCols());
    }

    @Test
    void constructorRejectsInvalidSize() {
        assertThrows(IllegalArgumentException.class, () -> new BoardModel(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new BoardModel(5, -1));
    }

    @Test
    void isValidPosition() {
        assertTrue(board.isValidPosition(0, 0));
        assertTrue(board.isValidPosition(6, 4));
        assertFalse(board.isValidPosition(-1, 0));
        assertFalse(board.isValidPosition(7, 0));
        assertFalse(board.isValidPosition(0, 5));
    }

    @Test
    void placePieceAndGetPieceAt() {
        board.placePiece(3, 2, "wolf-1");
        assertEquals("wolf-1", board.getPieceAt(3, 2));
        assertTrue(board.isOccupied(3, 2));
    }

    @Test
    void placePieceOnOccupiedThrows() {
        board.placePiece(0, 0, "a");
        assertThrows(IllegalStateException.class, () -> board.placePiece(0, 0, "b"));
    }

    @Test
    void placePieceNullIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> board.placePiece(0, 0, null));
    }

    @Test
    void removePiece() {
        board.placePiece(1, 1, "x");
        board.removePiece(1, 1);
        assertFalse(board.isOccupied(1, 1));
        assertNull(board.getPieceAt(1, 1));
    }

    @Test
    void movePiece() {
        board.placePiece(0, 0, "mover");
        board.movePiece(0, 0, 3, 3);
        assertNull(board.getPieceAt(0, 0));
        assertEquals("mover", board.getPieceAt(3, 3));
    }

    @Test
    void movePieceFromEmptyThrows() {
        assertThrows(IllegalStateException.class, () -> board.movePiece(0, 0, 1, 1));
    }

    @Test
    void movePieceToOccupiedThrows() {
        board.placePiece(0, 0, "a");
        board.placePiece(1, 1, "b");
        assertThrows(IllegalStateException.class, () -> board.movePiece(0, 0, 1, 1));
    }

    @Test
    void outOfBoundsThrows() {
        assertThrows(IllegalArgumentException.class, () -> board.getPieceAt(10, 0));
        assertThrows(IllegalArgumentException.class, () -> board.placePiece(-1, 0, "x"));
    }

    @Test
    void getAdjacentPositions_center() {
        List<Position> adj = board.getAdjacentPositions(3, 2);
        assertEquals(4, adj.size());
        assertTrue(adj.contains(new Position(2, 2)));
        assertTrue(adj.contains(new Position(4, 2)));
        assertTrue(adj.contains(new Position(3, 1)));
        assertTrue(adj.contains(new Position(3, 3)));
    }

    @Test
    void getAdjacentPositions_corner() {
        List<Position> adj = board.getAdjacentPositions(0, 0);
        assertEquals(2, adj.size());
        assertTrue(adj.contains(new Position(1, 0)));
        assertTrue(adj.contains(new Position(0, 1)));
    }

    @Test
    void findPiece() {
        board.placePiece(4, 3, "target");
        assertEquals(new Position(4, 3), board.findPiece("target"));
        assertNull(board.findPiece("nonexistent"));
    }

    @Test
    void getSummonRow_normal() {
        assertEquals(0, board.getSummonRow(PieceAlignment.P1));
        assertEquals(6, board.getSummonRow(PieceAlignment.P2));
    }

    @Test
    void getSummonRow_flipped() {
        board.setFlipped(true);
        assertEquals(6, board.getSummonRow(PieceAlignment.P1));
        assertEquals(0, board.getSummonRow(PieceAlignment.P2));
    }

    @Test
    void isValidSummonPosition() {
        assertTrue(board.isValidSummonPosition(0, 2, PieceAlignment.P1));
        assertFalse(board.isValidSummonPosition(1, 2, PieceAlignment.P1));
        assertTrue(board.isValidSummonPosition(6, 2, PieceAlignment.P2));
        // Occupied
        board.placePiece(0, 0, "blocker");
        assertFalse(board.isValidSummonPosition(0, 0, PieceAlignment.P1));
    }

    @Test
    void positionDistances() {
        Position a = new Position(0, 0);
        Position b = new Position(3, 4);
        assertEquals(7, a.manhattanDistance(b));
        assertEquals(4, a.chebyshevDistance(b));
    }
}
