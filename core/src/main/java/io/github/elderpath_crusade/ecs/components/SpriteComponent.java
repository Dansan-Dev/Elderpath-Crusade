package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;
import io.github.elderpath_crusade.interfaces.Renderable;

/**
 * Holds the sprite/renderable for an entity. The renderable reference
 * is the actual drawable object used by the rendering system.
 */
public class SpriteComponent implements Component {
    public String spritePath;
    public Renderable renderable;

    public SpriteComponent set(String spritePath) {
        this.spritePath = spritePath;
        return this;
    }

    public SpriteComponent setRenderable(Renderable renderable) {
        this.renderable = renderable;
        return this;
    }
}
