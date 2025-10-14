package io.github.forest_of_dreams.data_objects;

import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.settings.GamePieceType;
import io.github.forest_of_dreams.interfaces.Renderable;
import lombok.Getter;

import java.util.HashMap;
import java.util.UUID;

public abstract class GamePiece {
    @Getter private final GamePieceStats stats;
    @Getter private final GamePieceType type;
    @Getter private PieceAlignment alignment;
    @Getter private final UUID id;
    @Getter private Renderable sprite;
    @Getter private final HashMap<GamePieceData, Object> data = new HashMap<>();

    public GamePiece(GamePieceStats stats, GamePieceType type, PieceAlignment alignment, UUID id, Renderable sprite) {
        this.stats = stats;
        this.type = type;
        this.alignment = alignment;
        this.id = id;
        this.sprite = sprite;
    }

    public Object getData(GamePieceData key) {
        return data.get(key);
    }

    public void updateData(GamePieceData key, Object value) {
        data.put(key, value);
    }
}
