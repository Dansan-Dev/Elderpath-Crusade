package io.github.forest_of_dreams.game_objects.cards;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.sprites.SpriteObject;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.path_loaders.ImagePathSpritesAndAnimations;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.utils.GraphicUtils;
import io.github.forest_of_dreams.utils.SpriteCreator;
import io.github.forest_of_dreams.managers.InteractionManager;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A Card that can display either its front or back and execute a play effect.
 * - Rendering: Only the currently visible side (front/back) is rendered.
 * - Effect: A functional CardEffect can be supplied to define behavior (e.g., summon a piece).
 */
public class Card extends HigherOrderTexture implements Clickable {
    @Getter private final Renderable front;
    @Getter private final Renderable back;

    // z-layer used by card art (also used for title/border overlays)
    private final int zLayer;

    @Getter @Setter
    private boolean faceUp;

    // Set for every card inside the deck
    @Setter
    private Runnable onConsumed = null;

    // Optional title overlay
    private Text title;
    private FontType titleFont = FontType.SILKSCREEN;
    private Color titleColor = Color.WHITE;

    // Animated selection border
    private float borderProgress = 0f; // 0..1
    private final float borderSpeed = 4f; // ~0.25s

    @FunctionalInterface
    public interface CardEffect {
        void apply(Board board, int row, int col);
    }

    // Preload base frames once to avoid reloading textures per card
    private static final List<Sprite> FRONT_BASE_FRAMES = List.of(
        SpriteCreator.makeSprite(
            ImagePathSpritesAndAnimations.CARD_FRONT.getPath(),
            0, 0,
            1024, 1536,
            125, 200
        )
    );
    private static final List<Sprite> BACK_BASE_FRAMES = List.of(
        SpriteCreator.makeSprite(
            ImagePathSpritesAndAnimations.CARD_BACK.getPath(),
            0, 0,
            1024, 1536,
            125, 200
        )
    );

    public Card(int x, int y, int width, int height, int z, CardEffect effect) {
        // Clone sprites so each Card has its own instances
        List<Sprite> frontFrames = new ArrayList<>(FRONT_BASE_FRAMES.size());
        for (Sprite s : FRONT_BASE_FRAMES) frontFrames.add(new Sprite(s));
        List<Sprite> backFrames = new ArrayList<>(BACK_BASE_FRAMES.size());
        for (Sprite s : BACK_BASE_FRAMES) backFrames.add(new Sprite(s));

        SpriteObject frontSprite = new SpriteObject(
            0, 0,
            width, height,
            z,
            SpriteBoxPos.BOTTOM_LEFT
        );
        frontSprite.addAnimation("general", frontFrames, 0);

        SpriteObject backSprite = new SpriteObject(
            0, 0,
            width, height,
            z,
            SpriteBoxPos.BOTTOM_LEFT
        );
        backSprite.addAnimation("general", backFrames, 0);

        this.zLayer = z;
        this.faceUp = true;
        this.front = frontSprite;
        this.back = backSprite;

        setBounds(new Box(x, y, width, height));

        // Attach children to this Card; they render relative to the Card's origin.
        attachChild(front);
        attachChild(back);
    }

    private void attachChild(Renderable child) {
        // Keep child positioned at (0,0) relative to the card's bounds if it already has bounds.
        if (child.getBounds() != null) {
            child.getBounds().setX(0);
            child.getBounds().setY(0);
        }
        child.setParent(new Box(0, 0, getBounds().getWidth(), getBounds().getHeight()));
    }

    @Override
    public void setBounds(Box bounds) {
        super.setBounds(bounds);
        front.getBounds().setWidth(bounds.getWidth());
        front.getBounds().setHeight(bounds.getHeight());
        back.getBounds().setWidth(bounds.getWidth());
        back.getBounds().setHeight(bounds.getHeight());
        // Keep title sizing in sync
        if (title != null) {
            title.withFontSize(Math.max(12, (int)(bounds.getHeight() * 0.15f)));
        }
    }

    public void flip() {
        this.faceUp = !this.faceUp;
    }

    public void showFront() { this.faceUp = true; }
    public void showBack() { this.faceUp = false; }
    public boolean isFaceDown() { return !faceUp; }

    // ---- Title API ----
    public void setTitle(String text, FontType fontType) {
        this.titleFont = (fontType != null ? fontType : this.titleFont);
        if (this.title == null) {
            this.title = new Text(text, this.titleFont, 0, 0, zLayer, titleColor);
        } else {
            this.title.setText(text);
            this.title.setFontType(this.titleFont);
        }
        // Size based on current bounds
        int h = getBounds().getHeight();
        this.title.withFontSize(Math.max(12, (int)(h * 0.15f)));
    }

    public void setTitleColor(Color color) { this.titleColor = (color == null ? Color.WHITE : color); }

    /**
     * Consume this card: triggers the configured onConsumed hook.
     * Intended to be called by concrete cards after their effect resolves.
     */
    public void consume() {
        if (onConsumed != null) onConsumed.run();
    }

    private Renderable activeSide() {
        return faceUp ? front : back;
    }

    @Override
    public List<Integer> getZs() {
        return activeSide().getZs();
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        activeSide().render(batch, zLevel, isPaused);
        // Overlays (title + border) only when face-up and at card z layer
        if (!isPaused && faceUp && zLevel == zLayer) {
            int[] abs = calculatePos();
            renderTitle(batch, zLevel, abs[0], abs[1]);
            renderBorderAnimation(batch, zLevel, abs[0], abs[1]);
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        activeSide().render(batch, zLevel, isPaused, x, y);
        if (!isPaused && faceUp && zLevel == zLayer) {
            renderTitle(batch, zLevel, x, y);
            renderBorderAnimation(batch, zLevel, x, y);
        }
    }

    private void renderTitle(SpriteBatch batch, int zLevel, int x, int y) {
        if (title != null) {
            int titleX = x + (getWidth() - title.getWidth()) / 2;
            int titleY = y + (int)(getHeight() * 0.75f) - title.getHeight() / 2;
            title.render(batch, zLevel, false, titleX, titleY);
        }
    }

    private void renderBorderAnimation(SpriteBatch batch, int zLevel, int x, int y) {
        boolean active = InteractionManager.hasActiveSelection() && InteractionManager.getActiveSource() == this;
        float dt = Gdx.graphics.getDeltaTime();
        if (active) borderProgress = Math.min(1f, borderProgress + borderSpeed * dt);
        else borderProgress = Math.max(0f, borderProgress - borderSpeed * dt);
        if (borderProgress > 0f) {
            int width = getWidth();
            int height = getHeight();
            int thickness = Math.max(2, Math.round(Math.min(width, height) * 0.08f * borderProgress));
            Color color = Color.WHITE;
            // Top
            batch.draw(GraphicUtils.getPixelTexture(color), x, y + height - thickness, width, thickness);
            // Bottom
            batch.draw(GraphicUtils.getPixelTexture(color), x, y, width, thickness);
            // Left
            batch.draw(GraphicUtils.getPixelTexture(color), x, y, thickness, height);
            // Right
            batch.draw(GraphicUtils.getPixelTexture(color), x + width - thickness, y, thickness, height);
        }
    }
}
