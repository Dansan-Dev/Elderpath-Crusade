package io.github.elderpath_crusade.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/** Names from outside (Steam profiles) must be safe to draw with a small pixel font. */
class DisplayNameTest {
    // a stand-in for the font: printable ASCII only
    private static final IntPredicate ASCII = cp -> cp >= 0x20 && cp < 0x7f;

    @Test
    void ordinaryName_isUnchanged() {
        assertEquals("Yxaria", DisplayName.forDisplay("Yxaria", 20, ASCII));
    }

    @Test
    void unsupportedCharacters_becomeQuestionMarks() {
        assertEquals("Ana?", DisplayName.forDisplay("Anaé", 20, ASCII));
        assertEquals("??", DisplayName.forDisplay("日本", 20, ASCII));
        assertEquals("a?b", DisplayName.forDisplay("a😀b", 20, ASCII), "an emoji (two UTF-16 units) is one '?'");
    }

    @Test
    void controlAndInvisibleCharacters_areDropped() {
        assertEquals("AB", DisplayName.forDisplay("A\u0000‮B​", 20, ASCII), "NUL, right-to-left override, zero-width space");
        assertEquals("AB", DisplayName.forDisplay("A\nB".replace("\n", "\u0007"), 20, ASCII));
    }

    @Test
    void whitespace_isCollapsedAndTrimmed() {
        assertEquals("A B C", DisplayName.forDisplay("  A \t\n B   C  ", 20, ASCII));
    }

    @Test
    void longName_isCutWithTwoDots() {
        String cut = DisplayName.forDisplay("x".repeat(40), 20, ASCII);
        assertEquals(20, cut.length());
        assertTrue(cut.endsWith(".."));
    }

    @Test
    void nullBlankOrTinyLimit_giveEmpty() {
        assertEquals("", DisplayName.forDisplay(null, 20, ASCII));
        assertEquals("", DisplayName.forDisplay("   ", 20, ASCII));
        assertEquals("", DisplayName.forDisplay("abc", 2, ASCII));
    }
}
