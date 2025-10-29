package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.gdx.graphics.g2d.Sprite;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.game_objects.sprites.SpriteObject;
import io.github.elderpath_crusade.interfaces.Clickable;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.path_loaders.ImagePathSpritesAndAnimations;
import io.github.elderpath_crusade.utils.SpriteCreator;
import io.github.elderpath_crusade.managers.TurnManager;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

public class Deck extends SpriteObject{
    @Getter
    private final List<Card> cards = new ArrayList<>();
    @Getter
    private final List<Card> discardPile = new ArrayList<>();
    @Getter @Setter
    private Hand hand;
    // Owner of this deck (P1 or P2)
    @Getter @Setter
    private PieceAlignment owner;

    private final Random rng = new Random();

    public Deck(List<Card> cards, int x, int y, int width, int height, int z, SpriteBoxPos spriteBoxPos) {
        super(x, y, width, height, z, spriteBoxPos);
        addNewCards(cards);

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

    public void addNewCards(List<Card> cards) {
        cards.forEach(card -> {
            card.setOnConsumed(() -> {
                hand.removeCard(card);
                discardPile.add(card);
            });
        });
        this.cards.addAll(cards);
    }

    public void draw() {
        if (hand == null) return;
        if (cards.isEmpty()) {
            if (discardPile.isEmpty()) return;
            shuffle();
        }
        Card c = cards.remove(0);
        hand.addCard(c);
        // Emit CARD_DRAWN
        EventBus.emit(
                GameEventType.CARD_DRAWN,
                Map.of(
                        "owner", (owner == null ? "UNKNOWN" : owner.name()),
                        "card", c.getClass().getSimpleName(),
                        "handSize", hand.getCards().size()
                )
        );
    }

    public void shuffle() {
        if (discardPile.isEmpty()) return;
        cards.addAll(discardPile);
        discardPile.clear();
        Collections.shuffle(cards, rng);
        // Emit CARD_SHUFFLED
        EventBus.emit(
                GameEventType.CARD_SHUFFLED,
                Map.of(
                        "owner", (owner == null ? "UNKNOWN" : owner.name()),
                        "deckSize", cards.size()
                )
        );
    }
}
