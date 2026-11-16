package io.github.elderpath_crusade.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextEntryTest {
    @Test
    void typedCharactersAccumulateAndBackspaceRemovesTheLast() {
        TextEntry entry = new TextEntry(10, false);
        entry.type("abc");
        entry.backspace();
        assertEquals("ab", entry.getText());
        entry.backspace();
        entry.backspace();
        entry.backspace();
        assertTrue(entry.isEmpty());
    }

    @Test
    void controlCharactersAreNeverAccepted() {
        TextEntry entry = new TextEntry(10, false);
        assertFalse(entry.typeChar('\n'));
        assertFalse(entry.typeChar('\b'));
        assertFalse(entry.typeChar((char) 127));
        assertFalse(entry.typeChar('\t'));
        entry.type("a\nb\u0007c");
        assertEquals("abc", entry.getText());
    }

    @Test
    void theLengthLimitIsEnforcedAlsoForPastedText() {
        TextEntry entry = new TextEntry(5, false);
        entry.type("0123456789");
        assertEquals("01234", entry.getText());
        assertFalse(entry.typeChar('x'));
    }

    @Test
    void aMaskedEntryHidesItsText() {
        TextEntry entry = new TextEntry("hunter2", 20, true);
        assertEquals("hunter2", entry.getText());
        assertEquals("*******", entry.display());
        assertEquals("abc", new TextEntry("abc", 20, false).display());
    }

    @Test
    void nullAndClearAreSafe() {
        TextEntry entry = new TextEntry("abc", 10, false);
        entry.type(null);
        entry.clear();
        assertTrue(entry.isEmpty());
        entry.backspace();
        assertEquals("", entry.display());
    }
}
