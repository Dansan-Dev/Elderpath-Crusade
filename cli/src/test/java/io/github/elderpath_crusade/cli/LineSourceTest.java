package io.github.elderpath_crusade.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The script source's own bookkeeping — no session, no I/O. */
class LineSourceTest {

    @Test
    void script_yieldsEachLineInOrderThenExhausts() throws InterruptedException {
        LineSource source = LineSource.script(List.of("board", "end", "quit"));

        assertFalse(source.exhausted());
        assertEquals("board", source.nextLine(0));
        assertEquals("end", source.nextLine(0));
        assertFalse(source.exhausted());
        assertEquals("quit", source.nextLine(0));
        assertTrue(source.exhausted());
        assertNull(source.nextLine(0));
    }

    @Test
    void script_ofNoLines_isImmediatelyExhausted() throws InterruptedException {
        LineSource source = LineSource.script(List.of());
        assertTrue(source.exhausted());
        assertNull(source.nextLine(0));
    }

    @Test
    void script_isNotInteractive() {
        assertFalse(LineSource.script(List.of()).interactive());
    }
}
