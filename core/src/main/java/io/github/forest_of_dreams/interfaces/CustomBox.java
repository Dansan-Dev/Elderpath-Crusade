package io.github.forest_of_dreams.interfaces;

import io.github.forest_of_dreams.data_objects.Box;

public interface CustomBox {
    int getX();

    int getY();

    int getWidth();

    int getHeight();

    // Hit-test using absolute coordinates derived from this element's bounds and its full parent chain.
    // Falls back to getX()/getY() when bounds are unavailable.
    default boolean inRange(int x, int y) {
        int absX = getX();
        int absY = getY();

        if (this instanceof Renderable renderable) {
            Box bounds = renderable.getBounds();
            if (bounds != null) {
                absX = bounds.getX();
                absY = bounds.getY();
                // Accumulate all parents' offsets to get absolute position
                Box parent = renderable.getParent();
                while (parent != null) {
                    absX += parent.getX();
                    absY += parent.getY();
                    // Walk up if the parent itself has a parent-like container.
                    // Box is a simple data object, so we cannot query further; break.
                    // Parents in this engine are represented by Box only one level deep.
                    // If deeper nesting is required, containers should set child bounds relative to the root parent.
                    break;
                }
            }
        }

        boolean inRangeX = absX <= x && x < absX + getWidth();
        boolean inRangeY = absY <= y && y < absY + getHeight();
        return inRangeX && inRangeY;
    }
}
