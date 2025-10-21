package io.github.forest_of_dreams.game_objects.cards;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.function.Supplier;

/**
 * A Card that can display either its front or back and execute a play effect.
 * - Rendering: Only the currently visible side (front/back) is rendered.
 * - Effect: A functional CardEffect can be supplied to define behavior (e.g., summon a piece).
 */
public class Card extends HigherOrderTexture {
    @Getter private final Renderable front;
    @Getter private final Renderable back;

    @Getter @Setter
    private boolean faceUp;

    @Getter
    private final CardEffect playEffect;

    @FunctionalInterface
    public interface CardEffect {
        void apply(Board board, int row, int col);
    }

    public Card(int x, int y, Renderable front, Renderable back, CardEffect effect) {
        if (front == null || back == null) {
            throw new IllegalArgumentException("Card requires both front and back Renderables.");
        }
        this.front = front;
        this.back = back;
        this.playEffect = effect;
        this.faceUp = true;

        if (
            front.getBounds().getWidth() != back.getBounds().getWidth() ||
            front.getBounds().getHeight() != back.getBounds().getHeight()
        ) throw new IllegalArgumentException("Card requires Renderables with equal dimensions.");

        setBounds(new Box(x, y, front.getBounds().getWidth(), front.getBounds().getHeight()));

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
     * Convenience factory: Create a card that summons a GamePiece to the target square when played.
     */
    public static Card summonCard(int x, int y, Renderable front, Renderable back, Supplier<GamePiece> gamePieceSupplier) {
        CardEffect effect = (board, row, col) -> {
            GamePiece gp = gamePieceSupplier.get();
            if (gp != null) {
                board.addGamePieceToPos(row, col, gp);
            }
        };
        return new Card(x, y, front, back, effect);
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
