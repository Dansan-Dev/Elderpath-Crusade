package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;
import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Which player owns this entity.
 */
public class AlignmentComponent implements Component {
    public PieceAlignment alignment;

    public AlignmentComponent set(PieceAlignment alignment) {
        this.alignment = alignment;
        return this;
    }
}
