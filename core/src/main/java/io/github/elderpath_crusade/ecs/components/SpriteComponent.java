package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

public class SpriteComponent implements Component {
    public String spritePath;

    public SpriteComponent set(String spritePath) {
        this.spritePath = spritePath;
        return this;
    }
}
