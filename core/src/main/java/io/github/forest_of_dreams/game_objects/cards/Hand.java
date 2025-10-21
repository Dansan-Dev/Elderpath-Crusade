package io.github.forest_of_dreams.game_objects.cards;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.supers.AbstractTexture;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class Hand extends HigherOrderTexture {
    @Getter
    private List<Card> cards;
    private int cardMargin = 2;
    @Getter @Setter
    private int centerX;
    @Getter @Setter
    private int bottomY;
    @Getter @Setter
    private int cardWidth;
    @Getter @Setter
    private int cardHeight;

    public Hand(int centerX, int bottomY, int cardWidth, int cardHeight) {
        cards = new ArrayList<>();
        this.centerX = centerX;
        this.bottomY = bottomY;
        this.cardWidth = cardWidth;
        this.cardHeight = cardHeight;
        setBounds(new Box(centerX, bottomY, 0, 0));
    }

    public void addCard(Card card) {
        cards.add(card);
        updateBounds();
    }

    public void updateBounds() {
        int totalCardWidth = cards.stream()
                .mapToInt(AbstractTexture::getWidth)
                .sum();
        int totalMarginWidth = cardMargin * (cards.size() - 1);
        int width = totalCardWidth + totalMarginWidth;
        setBounds(new Box(centerX, bottomY, width, cardHeight));

        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            int relX = i * (cardWidth + cardMargin);
            c.setBounds(
                new Box(
                    -(getWidth()/2) + relX,
                    0,
                    cardWidth,
                    cardHeight
                )
            );
        }
    }

    @Override
    public List<Integer> getZs() {
        return cards.stream()
            .map(Card::getZs)
            .flatMap(List::stream)
            .distinct()
            .toList();
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
