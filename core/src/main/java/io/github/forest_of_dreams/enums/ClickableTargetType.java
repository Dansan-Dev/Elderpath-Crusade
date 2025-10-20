package io.github.forest_of_dreams.enums;

import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.Plot;
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
}
