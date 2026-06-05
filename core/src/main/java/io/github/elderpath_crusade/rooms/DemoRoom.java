package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.CardFactory;
import io.github.elderpath_crusade.game_objects.cards.Deck;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.game.DeckManager;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.config.SettingsManager;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.game.TurnManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DemoRoom extends BattleRoom {

    private DemoRoom() {
        super(GameMode.DEMO);

        // Finalize layout
        layoutBoard();
    }

    @Override
    protected void onPassTurnClicked() {
        GameContext.get().getTurnManager().endTurn();
    }

    @Override
    protected Deck createDeck(PieceAlignment alignment, Hand hand) {
        List<Card> cards = new ArrayList<>();
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        if (alignment == PieceAlignment.P1) {
            // P1 Deck Logic
            if (GameContext.get().getDeckManager().hasDraftedDeck()) {
                List<Function<DeckManager.CardCreationParams, Card>> draftedDeck = GameContext.get().getDeckManager().getDraftedDeck();
                for (Function<DeckManager.CardCreationParams, Card> cardCreator : draftedDeck) {
                    DeckManager.CardCreationParams params = new DeckManager.CardCreationParams(
                        board, PieceAlignment.P1,
                        0, 0,
                        125, 200,
                        0
                    );
                    cards.add(cardCreator.apply(params));
                }
            } else {
                String[] fallbackDeck = {"Rogue","Fairy","Wind Spirit","Big Toad","Sniper",
                        "Barbarian","King","Charger","Crossbowman","Skeleton Bomber","Warp Mage"};
                for (int i = 0; i < 24; i++) {
                    String name = fallbackDeck[i % fallbackDeck.length];
                    DeckManager.CardCreationParams p = new DeckManager.CardCreationParams(board, PieceAlignment.P1, 0, 0, 125, 200, 0);
                    cards.add(CardFactory.create(name, p));
                }
            }
            Deck deck = new Deck(cards, 0, 10, 125, 200, 1, SpriteBoxPos.BOTTOM_LEFT);
            deck.shuffle();
            deck.getBounds().setX(screenW - deck.getWidth() - 10);
            deck.setOwner(PieceAlignment.P1);
            deck.setHand(hand);
            return deck;
        } else {
            // P2 Deck Logic
            for (int i = 0; i < 10; i++) {
                String name = (i % 2 == 0) ? "Rifleman" : "Rogue";
                DeckManager.CardCreationParams p = new DeckManager.CardCreationParams(board, PieceAlignment.P2, 0, 0, 125, 200, 0);
                cards.add(CardFactory.create(name, p));
            }
            Deck deck = new Deck(cards, 0, screenH - 200, 125, 200, 1, SpriteBoxPos.BOTTOM_LEFT);
            deck.shuffle();
            deck.getBounds().setX(screenW - deck.getWidth() - 10);
            deck.getBounds().setY(screenH - deck.getHeight() - 10);
            deck.setOwner(PieceAlignment.P2);
            deck.setHand(hand);
            return deck;
        }
    }

    public static DemoRoom get() {
        return new DemoRoom();
    }
}
