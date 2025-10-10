package io.github.forest_of_dreams.supers;

import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.interfaces.CustomBox;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all textures
 */
@Getter @Setter
public abstract class AbstractTexture implements CustomBox {
    private Box parent;
    private Box bounds;

    protected int[] calculatePos() {
        if (bounds == null) return new int[]{0, 0};
        if (parent == null) return new int[]{bounds.getX(), bounds.getY()};
        return new int[]{parent.getX() + bounds.getX(), parent.getY() + bounds.getY()};
    }

    public int getX() {
        return bounds.getX();
    }

    public int getY() {
        return bounds.getY();
    }

    public int getWidth() {
        return bounds.getWidth();
    }

    public int getHeight() {
        return bounds.getHeight();
    }
}
