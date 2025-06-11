package io.github.forest_of_dreams.supers;

import io.github.forest_of_dreams.interfaces.Renderable;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public abstract class BaseTexture extends AbstractTexture implements Renderable {
    protected int z;

    public BaseTexture(int z) {
        this.z = z;
    }

    @Override
    public List<Integer> getZs() {
        return List.of(z);
    }
}
