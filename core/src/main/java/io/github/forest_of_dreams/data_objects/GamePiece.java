package io.github.forest_of_dreams.data_objects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.settings.GamePieceType;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.interfaces.OnClick;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.supers.AbstractTexture;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public abstract class GamePiece extends AbstractTexture implements Renderable, Clickable {
    @Getter private final GamePieceStats stats;
    @Getter private final GamePieceType type;
    @Getter private PieceAlignment alignment;
    @Getter private final UUID id;
    @Getter private Renderable sprite;
    @Getter private final HashMap<GamePieceData, Object> data = new HashMap<>();

    private OnClick onClick = null;
    @Getter private ClickableEffectData clickableEffectData = null;

    public GamePiece(GamePieceStats stats, GamePieceType type, PieceAlignment alignment, UUID id, Renderable sprite) {
        this.stats = stats;
        this.type = type;
        this.alignment = alignment;
        this.id = id;
        setSprite(sprite);

        this.clickableEffectData = ClickableEffectData.getImmediate();
        setClickableEffect(
            (e) -> {
                System.out.println("GAMEPIECE CLICKED");
            },
            clickableEffectData
        );
    }

    public void setSprite(Renderable sprite) {
        this.sprite = sprite;
        this.setBounds(sprite.getBounds());
    }

    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        this.onClick.run(interactionEntities);
    }

    public Object getData(GamePieceData key) {
        return data.get(key);
    }

    public void updateData(GamePieceData key, Object value) {
        data.put(key, value);
    }

    @Override
    public List<Integer> getZs() {
        return List.of(sprite.getZs().get(0));
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        sprite.render(batch, zLevel, isPaused);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        sprite.render(batch, zLevel, isPaused, x, y);
    }
}
