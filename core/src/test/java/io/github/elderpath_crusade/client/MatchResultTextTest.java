package io.github.elderpath_crusade.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MatchResultTextTest {

    @Test
    void youWon_saysYourNameWins_withNoSecondLine() {
        MatchResultText text = MatchResultText.online("Yxaria", "Baus", true);
        assertEquals("Yxaria wins!", text.headline());
        assertNull(text.subline());
    }

    @Test
    void youLost_saysTheOpponentWins_andYouLose() {
        MatchResultText text = MatchResultText.online("Yxaria", "Baus", false);
        assertEquals("Baus wins!", text.headline());
        assertEquals("You lose", text.subline());
    }

    @Test
    void unknownNames_fallBackToWords() {
        assertEquals("You wins!", MatchResultText.online(null, null, true).headline());
        assertEquals("Your opponent wins!", MatchResultText.online("Yxaria", " ", false).headline());
    }
}
