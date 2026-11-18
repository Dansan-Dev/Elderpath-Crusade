package io.github.elderpath_crusade.ui_objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** UI-010: picker lists of any length stay on screen, below the header, without overlapping. */
class ButtonListFittingTest {
    // a 1280x720 window: header zone ends at y=535, 40 px kept at the bottom
    private static final int TOP = 535, BOTTOM = 40, W = 260, H = 60, SPACING = 70, CX = 640, CY = 360;

    private static int[][] slots(int n) {
        return ButtonList.fittingSlots(n, SPACING, W, H, 20, CX, CY, TOP, BOTTOM);
    }

    @Test
    void aShortList_isOneCenteredColumn_likeBefore() {
        int[][] s = slots(4);
        for (int[] slot : s) assertEquals(CX - W / 2, slot[0]);
        assertEquals(SPACING, s[0][1] - s[1][1]);
        // vertically centred on CY, exactly as alignButtonsAcrossYAxis does
        assertEquals(CY + 3 * (SPACING / 2) - H / 2, s[0][1]);
    }

    @Test
    void aWholeDeck_usesMoreColumns_andNeverLeavesTheScreenOrCoversTheTitle() {
        for (int n : new int[] {7, 9, 15, 20, 28}) {
            int[][] s = slots(n);
            boolean multiColumn = false;
            for (int i = 0; i < n; i++) {
                assertTrue(s[i][1] >= BOTTOM - 1, n + " buttons: slot " + i + " below the bottom margin");
                assertTrue(s[i][1] + H <= TOP + 1, n + " buttons: slot " + i + " up into the header");
                assertTrue(s[i][0] >= 0 && s[i][0] + W <= 1280, n + " buttons: slot " + i + " off the sides");
                if (s[i][0] != s[0][0]) multiColumn = true;
                for (int j = 0; j < i; j++) {
                    boolean apartX = Math.abs(s[i][0] - s[j][0]) >= W;
                    boolean apartY = Math.abs(s[i][1] - s[j][1]) >= H;
                    assertTrue(apartX || apartY, n + " buttons: " + i + " overlaps " + j);
                }
            }
            if (n >= 9) assertTrue(multiColumn, n + " buttons should need a second column");
        }
    }

    @Test
    void order_runsTopToBottomThenLeftToRight() {
        int[][] s = slots(12);
        assertTrue(s[1][1] < s[0][1]);          // next one is below
        int firstOfSecondColumn = 0;
        while (s[firstOfSecondColumn][0] == s[0][0]) firstOfSecondColumn++;
        assertTrue(s[firstOfSecondColumn][0] > s[0][0]);
        assertEquals(s[0][1], s[firstOfSecondColumn][1]); // a new column starts at the top
    }
}
