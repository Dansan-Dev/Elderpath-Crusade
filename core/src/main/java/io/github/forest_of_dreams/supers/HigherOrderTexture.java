package io.github.forest_of_dreams.supers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.interfaces.Renderable;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Higher order texture that can contain other textures
 */
@Getter @Setter
public abstract class HigherOrderTexture extends LowestOrderTexture implements Renderable {
    private List<Renderable> renderables = new ArrayList<>();

    @Override
    public List<Integer> getZs() {
        return getRenderables().stream()
            .map(Renderable::getZs)
            .flatMap(List::stream)
            .toList();
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        renderables.forEach(r -> r.render(batch, zLevel, isPaused));
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        renderables.forEach(r -> r.render(batch, zLevel, isPaused, x, y));
    }
}
