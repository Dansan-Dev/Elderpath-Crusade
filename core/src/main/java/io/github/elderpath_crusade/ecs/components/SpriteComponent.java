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
    /** Reference to the owning piece for status effect queries (stun/exhaustion). */
    public io.github.elderpath_crusade.game_objects.board.MonsterGamePiece piece;

    public SpriteComponent set(String spritePath) {
        this.spritePath = spritePath;
        return this;
    }

    public SpriteComponent setRenderable(Renderable renderable) {
        this.renderable = renderable;
        return this;
    }

    public SpriteComponent setPiece(io.github.elderpath_crusade.game_objects.board.MonsterGamePiece piece) {
        this.piece = piece;
        return this;
    }
}
