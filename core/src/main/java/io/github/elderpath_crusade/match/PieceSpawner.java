package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.enums.PieceAlignment;

/** Creates (and adds to the ECS engine) the entity for a summoned piece. */
@FunctionalInterface
public interface PieceSpawner {
    Entity spawn(PieceDefinition definition, PieceAlignment owner, int row, int col);
}
