package io.github.elderpath_crusade.supers;

import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.interfaces.CustomBox;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all textures
 */
@Getter @Setter
public abstract class LowestOrderTexture implements CustomBox {
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
