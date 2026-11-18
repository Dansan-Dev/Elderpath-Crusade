package io.github.elderpath_crusade.ui_objects;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.supers.HigherOrderTexture;

import java.util.List;
import java.util.stream.IntStream;

/**
 * A simple container for grouping Button instances, analogous to TextList for Text.
 * It allows adding buttons and aligning them across X or Y axes around a center point
 * with a specified offset between items.
 */
public class ButtonList extends HigherOrderTexture {

    public ButtonList() {}

    public ButtonList(List<Button> buttons) {
        getRenderables().addAll(buttons);
    }

    public void addButton(Button button) {
        getRenderables().add(button);
    }

    /**
     * Align buttons horizontally centered at centerX with vertical center at centerY.
     * Each subsequent button is placed offset pixels apart along the X axis.
     */
    public void alignButtonsAcrossXAxis(int offset, int centerX, int centerY) {
        int size = getRenderables().size();
        if (size == 0) return;
        int startX = centerX - (size - 1) * (offset / 2);
        IntStream.range(0, size).forEach(i -> {
            Button b = (Button) getRenderables().get(i);
            Box bounds = b.getBounds();
            int x = startX + i * offset - bounds.getWidth() / 2; // center each button at its slot
            int y = centerY - bounds.getHeight() / 2;            // vertically centered around centerY
            bounds.setX(x);
            bounds.setY(y);
        });
    }

    /**
     * Align buttons vertically centered at centerY with horizontal center at centerX.
     * Each subsequent button is placed offset pixels apart along the Y axis.
     */
    public void alignButtonsAcrossYAxis(int offset, int centerX, int centerY) {
        int size = getRenderables().size();
        if (size == 0) return;
        int startY = centerY + (size - 1) * (offset / 2);
        IntStream.range(0, size).forEach(i -> {
            Button b = (Button) getRenderables().get(i);
            Box bounds = b.getBounds();
            int x = centerX - bounds.getWidth() / 2;             // horizontally centered around centerX
            int y = startY - i * offset - bounds.getHeight() / 2; // center each button at its slot
            bounds.setX(x);
            bounds.setY(y);
        });
    }

    /** Clear space kept above a picker list for the room's header and flavour line (they sit 100 and 150 px below the top). */
    private static final int FITTING_TOP_CLEARANCE = 185;
    private static final int FITTING_BOTTOM_CLEARANCE = 40;
    private static final int FITTING_COLUMN_GAP = 20;

    /**
     * Like {@link #alignButtonsAcrossYAxis} for a list of any length (UI-010): while the buttons fit in one column
     * below the room's header they are laid out exactly as before; a longer list (a whole deck) is split into as
     * many columns as it needs, filled top to bottom, so nothing runs off the screen or over the title.
     */
    public void alignButtonsFitting(int spacing, int centerX, int centerY) {
        int size = getRenderables().size();
        if (size == 0) return;
        Box first = ((Button) getRenderables().get(0)).getBounds();
        int screenHeight = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        int[][] slots = fittingSlots(size, spacing, first.getWidth(), first.getHeight(), FITTING_COLUMN_GAP, centerX, centerY,
                screenHeight - FITTING_TOP_CLEARANCE, FITTING_BOTTOM_CLEARANCE);
        for (int i = 0; i < size; i++) {
            Box bounds = ((Button) getRenderables().get(i)).getBounds();
            bounds.setX(slots[i][0]);
            bounds.setY(slots[i][1]);
        }
    }

    /** Bottom-left corner of every button of {@link #alignButtonsFitting}: pure arithmetic, so it is unit-tested. */
    static int[][] fittingSlots(int count, int spacing, int buttonWidth, int buttonHeight, int columnGap,
                                int centerX, int centerY, int topY, int bottomY) {
        int[][] slots = new int[count][2];
        int rowsThatFit = Math.max(1, (topY - bottomY - buttonHeight) / spacing + 1);
        if (count <= rowsThatFit) {
            int startY = Math.min(centerY + (count - 1) * (spacing / 2), topY - buttonHeight / 2);
            for (int i = 0; i < count; i++) {
                slots[i][0] = centerX - buttonWidth / 2;
                slots[i][1] = startY - i * spacing - buttonHeight / 2;
            }
            return slots;
        }
        int columns = (count + rowsThatFit - 1) / rowsThatFit;
        int rows = (count + columns - 1) / columns; // balance the columns instead of leaving a stub
        int columnPitch = buttonWidth + columnGap;
        int startX = centerX - (columns - 1) * columnPitch / 2;
        int startY = topY - buttonHeight / 2;
        for (int i = 0; i < count; i++) {
            slots[i][0] = startX + (i / rows) * columnPitch - buttonWidth / 2;
            slots[i][1] = startY - (i % rows) * spacing - buttonHeight / 2;
        }
        return slots;
    }
}
