package io.github.elderpath_crusade.supers;

import io.github.elderpath_crusade.interfaces.Renderable;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Bare minimum texture implementation
 */
@Getter @Setter
public abstract class BaseTexture extends LowestOrderTexture implements Renderable {
    protected int z;

    public BaseTexture(int z) {
        this.z = z;
    }

    @Override
    public List<Integer> getZs() {
        return List.of(z);
    }
}
