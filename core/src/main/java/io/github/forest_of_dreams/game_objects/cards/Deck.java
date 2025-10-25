package io.github.forest_of_dreams.game_objects.cards;

import com.badlogic.gdx.graphics.g2d.Sprite;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.sprites.SpriteObject;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.interfaces.OnClick;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.multiplayer.EventBus;
import io.github.forest_of_dreams.multiplayer.GameEventType;
import io.github.forest_of_dreams.path_loaders.ImagePathSpritesAndAnimations;
import io.github.forest_of_dreams.utils.SpriteCreator;
import io.github.forest_of_dreams.managers.TurnManager;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

public class Deck extends SpriteObject implements Clickable {
    @Getter
    private final List<Card> cards = new ArrayList<>();
    @Getter
    private final List<Card> discardPile = new ArrayList<>();
    @Getter @Setter
    private Hand hand;
    // Owner of this deck (P1 or P2)
    @Getter @Setter
    private PieceAlignment owner;

    private OnClick onClick;
    private ClickableEffectData clickableEffectData;

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
        setClickableEffect(
            (e) -> draw(),
            ClickableEffectData.getImmediate()
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

    // Ensure InteractionManager can read the effect config by storing it here
    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
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

    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
         if (this.onClick == null) return;
         onClick.run(interactionEntities);
    };

    @Override
    public ClickableEffectData getClickableEffectData() {
        // Only the current player's deck is active during their turn.
        // Additionally, when P2 bot is enabled, block human clicks on P2's deck (the bot will trigger directly).
        if (owner == null) return clickableEffectData;
        if (owner == PieceAlignment.P2 && SettingsManager.debug.enableP2Bot) {
            return null;
        }
        return (owner == TurnManager.getCurrentPlayer()) ? clickableEffectData : null;
    }
}
