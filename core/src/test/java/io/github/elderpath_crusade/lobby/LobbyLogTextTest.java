package io.github.elderpath_crusade.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** User-supplied names go into the lobby's event log (OPS-001): they must not be able to forge or flood log lines. */
class LobbyLogTextTest {

    @Test
    void safe_controlCharacters_areReplaced() {
        String forged = LobbyServer.safe("Eve\nLobby 1 closed\r\u0000\u007f");
        assertFalse(forged.contains("\n"));
        assertFalse(forged.contains("\r"));
        assertFalse(forged.contains("\u0000"));
        assertEquals("Eve?Lobby 1 closed???", forged);
    }

    @Test
    void safe_longText_isTruncatedToForty() {
        assertEquals(40, LobbyServer.safe("x".repeat(500)).length());
    }

    @Test
    void safe_null_isQuestionMark() {
        assertEquals("?", LobbyServer.safe(null));
    }

    @Test
    void safe_ordinaryName_isUnchanged() {
        assertTrue(LobbyServer.safe("Yxaria").equals("Yxaria"));
    }
}
