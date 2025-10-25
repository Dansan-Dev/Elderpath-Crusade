package io.github.forest_of_dreams.enums;

import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.Plot;
import io.github.forest_of_dreams.interfaces.CustomBox;
import lombok.Getter;

import java.util.Set;

public enum ClickableTargetType {
    NONE(),
    GAME_PIECE(GamePiece.class),
    PLOT(Plot.class);

    @Getter private final Set<Class<?>> allowedClasses;

    ClickableTargetType(Class<?>... allowedClasses) {
        this.allowedClasses = Set.of(allowedClasses);
    }

    /**
     * Returns true if the given box matches this target type.
     * NONE matches everything. Otherwise, at least one allowed class must match.
     */
    public boolean matches(CustomBox box) {
        if (this == NONE) return true;
        if (box == null) return false;
        for (Class<?> allowed : allowedClasses) {
            if (allowed.isInstance(box)) return true;
        }
        return false;
    }
}
