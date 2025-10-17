package io.github.forest_of_dreams.data_objects;

import io.github.forest_of_dreams.supers.HigherOrderTexture;

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
}
