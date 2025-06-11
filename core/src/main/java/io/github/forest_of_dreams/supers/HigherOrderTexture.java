package io.github.forest_of_dreams.supers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.interfaces.Renderable;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public abstract class HigherOrderTexture extends AbstractTexture implements Renderable{
    private List<Renderable> renderables = new ArrayList<>();

    @Override
    public List<Integer> getZs() {
        return getRenderables().stream()
            .map(Renderable::getZs)
            .flatMap(List::stream)
            .toList();
    }

    @Override
    public void render(SpriteBatch batch, int zLevel) {
        List<Renderable> renderables = getRenderables();
        renderables.forEach(r -> r.render(batch, zLevel));
    }
}
