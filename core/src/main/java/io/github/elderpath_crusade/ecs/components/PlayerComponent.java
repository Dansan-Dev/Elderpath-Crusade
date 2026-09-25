package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;
import io.github.elderpath_crusade.enums.PieceAlignment;

/** ECS source of truth for a player's mana. One entity per PieceAlignment, owned by PlayerSystem. */
public class PlayerComponent implements Component {
    public PieceAlignment alignment;
    public int mana;
}
