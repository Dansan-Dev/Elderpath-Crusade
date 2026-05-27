package io.github.elderpath_crusade.model.piece;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.model.board.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PieceModelTest {

    private PieceModel piece;

    @BeforeEach
    void setUp() {
        piece = new PieceModel("wolf-1", "Wolf", PieceAlignment.P1,
                new PieceStats(1, 3, 2, 1, 1));
    }

    @Test
    void constructorSetsFields() {
        assertEquals("wolf-1", piece.getId());
        assertEquals("Wolf", piece.getName());
        assertEquals(PieceAlignment.P1, piece.getAlignment());
        assertEquals(3, piece.getCurrentHealth());
        assertEquals(0, piece.getRemainingActions()); // summoning sickness
    }

    @Test
    void dealDamageReducesHealth() {
        piece.dealDamage(2);
        assertEquals(1, piece.getCurrentHealth());
        assertFalse(piece.isDead());
    }

    @Test
    void dealDamageKills() {
        piece.dealDamage(5);
        assertTrue(piece.isDead());
    }

    @Test
    void dealDamageZeroDoesNothing() {
        piece.dealDamage(0);
        assertEquals(3, piece.getCurrentHealth());
    }

    @Test
    void healCapsAtMax() {
        piece.dealDamage(2);
        assertTrue(piece.heal(10));
        assertEquals(3, piece.getCurrentHealth());
    }

    @Test
    void healReturnsFalseAtFull() {
        assertFalse(piece.heal(1));
    }

    @Test
    void spendAction() {
        piece.resetActions();
        assertEquals(1, piece.getRemainingActions());
        piece.spendAction();
        assertEquals(0, piece.getRemainingActions());
        piece.spendAction(); // never below 0
        assertEquals(0, piece.getRemainingActions());
    }

    @Test
    void canAct() {
        assertFalse(piece.canAct()); // summoning sickness
        piece.resetActions();
        assertTrue(piece.canAct());
    }

    @Test
    void stunPreventsAction() {
        piece.resetActions();
        piece.stun(2);
        assertTrue(piece.isStunned());
        assertFalse(piece.canAct());
        piece.tickStun();
        assertTrue(piece.isStunned());
        piece.tickStun();
        assertFalse(piece.isStunned());
        assertTrue(piece.canAct());
    }

    @Test
    void positionTracking() {
        assertNull(piece.getPosition());
        piece.setPosition(new Position(3, 2));
        assertEquals(new Position(3, 2), piece.getPosition());
    }

    @Test
    void pieceStatsValidation() {
        assertThrows(IllegalArgumentException.class, () -> new PieceStats(1, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PieceStats(-1, 1, 1, 1, 1));
    }
}
