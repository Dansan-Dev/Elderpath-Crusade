package io.github.elderpath_crusade.session;

import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {

    @Test
    void createSession_hasCorrectInitialState() {
        GameSession session = GameSession.create(GameMode.DEMO, null);
        assertEquals(GameMode.DEMO, session.getMode());
        assertNull(session.getBoard());
        assertEquals(PieceAlignment.P1, session.getCurrentPlayer());
        assertEquals(0, session.getTurnNumber());
        assertFalse(session.isActive());
    }

    @Test
    void start_activatesSession() {
        GameSession session = GameSession.create(GameMode.LOCAL_MATCH, null);
        session.start();
        assertTrue(session.isActive());
        assertEquals(1, session.getTurnNumber());
    }

    @Test
    void end_deactivatesSession() {
        GameSession session = GameSession.create(GameMode.DEMO, null);
        session.start();
        session.end();
        assertFalse(session.isActive());
    }

    @Test
    void advanceTurn_updatesPlayerAndTurnNumber() {
        GameSession session = GameSession.create(GameMode.DEMO, null);
        session.start();
        session.advanceTurn(PieceAlignment.P2);
        assertEquals(PieceAlignment.P2, session.getCurrentPlayer());
        assertEquals(2, session.getTurnNumber());
    }
}
