package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.Updatable;
import io.github.elderpath_crusade.supers.LowestOrderTexture;

import java.util.List;
import java.util.function.Consumer;

/** Renders nothing — exists only so a room gets a call every frame (GraphicsManager updates Updatable content). */
final class Ticker extends LowestOrderTexture implements Renderable, Updatable {
    private final Consumer<Float> onUpdate;

    Ticker(Consumer<Float> onUpdate) {
        this.onUpdate = onUpdate;
        setBounds(new Box(0, 0, 0, 0));
    }

    @Override
    public List<Integer> getZs() {
        return List.of();
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
    }

    @Override
    public void update(float delta) {
        onUpdate.accept(delta);
    }
}
