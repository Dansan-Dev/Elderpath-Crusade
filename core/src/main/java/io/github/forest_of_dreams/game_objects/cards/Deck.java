package io.github.forest_of_dreams.game_objects.cards;

import com.badlogic.gdx.graphics.g2d.Sprite;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.sprites.SpriteObject;
import io.github.forest_of_dreams.path_loaders.ImagePathSpritesAndAnimations;
import io.github.forest_of_dreams.utils.SpriteCreator;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Deck extends SpriteObject {
    @Getter
    private final List<Card> cards;
    @Getter
    private final List<Card> discardPile;
    @Getter @Setter
    private Hand hand;

    private final Random rng = new Random();

    public Deck(List<Card> cards, int x, int y, int width, int height, int z, SpriteBoxPos spriteBoxPos) {
        super(x, y, width, height, z, spriteBoxPos);
        this.cards = cards;
        this.discardPile = new ArrayList<>();
        Sprite sprite = SpriteCreator.makeSprite(
            ImagePathSpritesAndAnimations.CARD_BACK.getPath(),
            0, 0,
            1024, 1536,
            125, 200
        );
        addAnimation(
            "general",
            List.of(sprite),
            0
        );
    }

    public void draw() {
        if (hand == null) return;
        if (cards.isEmpty()) {
            if (discardPile.isEmpty()) return;
            shuffle();
        }
        hand.addCard(cards.remove(0));
    }

    public void shuffle() {
        if (discardPile.isEmpty()) return;
        cards.addAll(discardPile);
        discardPile.clear();
        Collections.shuffle(cards, rng);
    }
}
