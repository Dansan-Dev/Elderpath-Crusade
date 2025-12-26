package io.github.elderpath_crusade.game_objects.board.game_piece;

import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.interfaces.Renderable;
import lombok.Getter;

import java.util.HashMap;
import java.util.UUID;

public abstract class GamePiece {
    @Getter protected final GamePieceStats stats;
    @Getter protected final GamePieceType type;
    @Getter protected PieceAlignment alignment;
    @Getter protected final UUID id;
    @Getter protected Renderable sprite;
    @Getter protected final HashMap<GamePieceData, Object> data = new HashMap<>();

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
