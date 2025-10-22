package io.github.forest_of_dreams.game_objects.cards;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.sprites.SpriteObject;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.path_loaders.ImagePathSpritesAndAnimations;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.utils.SpriteCreator;
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

    @Getter @Setter
    private boolean faceUp;

    @Getter
    private final CardEffect playEffect;

    @Setter
    private Runnable onConsumed = null;

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

        this.faceUp = true;
        this.front = frontSprite;
        this.back = backSprite;
        this.playEffect = effect;

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
    }

    public void flip() {
        this.faceUp = !this.faceUp;
    }

    public void showFront() { this.faceUp = true; }
    public void showBack() { this.faceUp = false; }
    public boolean isFaceDown() { return !faceUp; }

    /**
     * Execute the card's play effect on the provided board at the specified position.
     */
    public void play(Board board, int row, int col) {
        if (playEffect != null) {
            playEffect.apply(board, row, col);
        }
    }

    /**
     * Consume this card: triggers the configured onConsumed hook.
     * Intended to be called by concrete cards after their effect resolves.
     */
    public void consume() {
        if (onConsumed != null) onConsumed.run();
    }

    /**
     * Convenience factory: Create a card that summons a GamePiece to the target square when played.
     */
    public static Card summonCard(int x, int y, int width, int height, int z, Supplier<GamePiece> gamePieceSupplier) {
        CardEffect effect = (board, row, col) -> {
            GamePiece gp = gamePieceSupplier.get();
            if (gp != null) {
                board.addGamePieceToPos(row, col, gp);
            }
        };
        return new Card(x, y, width, height, z, effect);
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
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        activeSide().render(batch, zLevel, isPaused, x, y);
    }
}
