package io.github.forest_of_dreams.characters.pieces;

import io.github.forest_of_dreams.characters.sprites.GoblinSprite;
import io.github.forest_of_dreams.data_objects.GamePiece;
import io.github.forest_of_dreams.enums.settings.GamePieceType;

import java.util.UUID;

public class Goblin extends GamePiece {
    public Goblin(int x, int y, int width, int height) {
        super(
            GamePieceType.MONSTER,
            UUID.randomUUID(),
            new GoblinSprite(x, y, width, height)
        );
    }
}
