package io.github.forest_of_dreams.data_objects;

import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.enums.settings.GamePieceType;
import lombok.Getter;

import java.util.HashMap;
import java.util.UUID;

public class GamePiece {
    @Getter private final GamePieceType type;
    @Getter private final UUID id;
    @Getter private final HashMap<GamePieceData, Object> data = new HashMap<>();

    public GamePiece(GamePieceType type, UUID id) {
        this.type = type;
        this.id = id;
    }

    public Object getData(GamePieceData key) {
        return data.get(key);
    }

    public void updateData(GamePieceData key, Object value) {
        data.put(key, value);
    }

}
