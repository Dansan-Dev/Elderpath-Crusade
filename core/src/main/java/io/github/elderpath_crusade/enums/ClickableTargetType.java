package io.github.elderpath_crusade.enums;

import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import lombok.Getter;

import java.util.Set;

public enum ClickableTargetType {
    NONE(),
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
