package io.github.forest_of_dreams.supers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Higher order texture for UI elements
 */
@Getter
@Setter
public class HigherOrderUI extends LowestOrderTexture implements UIRenderable {
    private List<UIRenderable> renderableUIs = new ArrayList<>();

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        // Iterate over a snapshot to avoid ConcurrentModificationException if children mutate during render
        List<UIRenderable> snapshot = new ArrayList<>(renderableUIs);
        snapshot.forEach(r -> r.renderUI(batch, isPaused));
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        // Iterate over a snapshot to avoid ConcurrentModificationException if children mutate during render
        List<UIRenderable> snapshot = new ArrayList<>(renderableUIs);
        snapshot.forEach(r -> r.renderUI(batch, isPaused));
    }
}
