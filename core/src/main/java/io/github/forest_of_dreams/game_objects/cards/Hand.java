package io.github.forest_of_dreams.game_objects.cards;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.managers.InteractionManager;
import io.github.forest_of_dreams.supers.LowestOrderTexture;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class Hand extends HigherOrderTexture {
    @Getter
    private List<Card> cards;
    // Owner of this hand (P1 or P2)
    @Getter @Setter
    private PieceAlignment owner;
    private int cardMargin = 2;
    @Getter @Setter
    private int centerX;
    @Getter @Setter
    private int bottomY;
    @Getter @Setter
    private int cardWidth;
    @Getter @Setter
    private int cardHeight;
    @Getter
    private final int z;

    public Hand(int centerX, int bottomY, int cardWidth, int cardHeight, int z) {
        cards = new ArrayList<>();
        this.centerX = centerX;
        this.bottomY = bottomY;
        this.cardWidth = cardWidth;
        this.cardHeight = cardHeight;
        this.z = z;
        setBounds(new Box(centerX, bottomY, 0, 0));
        updateBounds();
    }

    public void addCard(Card card) {
        cards.add(card);
        // Register with InteractionManager if the card is clickable
        InteractionManager.addClickable(card);
        updateBounds();
    }

    public void updateBounds() {
        int totalCardWidth = cards.stream()
                .mapToInt(LowestOrderTexture::getWidth)
                .sum();
        int totalMarginWidth = cardMargin * (cards.size() - 1);
        int width = totalCardWidth + totalMarginWidth;
        setBounds(new Box(centerX, bottomY, width, cardHeight));

        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            int relX = i * (cardWidth + cardMargin);
            // Position card relative to Hand container and ensure hit-testing uses Hand as parent
            c.setBounds(
                new Box(
                    -(getWidth()/2) + relX,
                    0,
                    cardWidth,
                    cardHeight
                )
            );
            c.setParent(getBounds());
        }
    }

    public void removeCard(Card card) {
        if (card == null) return;
        cards.remove(card);
        InteractionManager.removeClickable(card);
        updateBounds();
    }

    @Override
    public List<Integer> getZs() {
        return List.of(z);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        cards.forEach(c -> c.render(batch, zLevel, isPaused));
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        cards.forEach(c -> c.render(
                batch,
                zLevel,
                isPaused,
                x + c.getX(),
                y + c.getY()
            )
        );
    }
}
