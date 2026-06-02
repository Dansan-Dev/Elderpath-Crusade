package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.cards.*;
import io.github.elderpath_crusade.game_objects.cards.Card;
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
                for (int i = 0; i < 24; i++) {
                    int kind = i % 11;
                    switch (kind) {
                        case 1 -> cards.add(new RogueCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                        case 2 -> cards.add(new FairyCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                        case 3 -> cards.add(new WindSpiritCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                        case 4 -> cards.add(new BigToadCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                        case 5 -> cards.add(new SniperCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                        case 6 -> cards.add(new BarbarianCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                        case 7 -> cards.add(new KingCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                        case 8 -> cards.add(new ChargerCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                        case 9 -> cards.add(new CrossbowmanCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                        case 10 -> cards.add(new SkeletonBomberCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                        default -> cards.add(new WarpMageCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    }
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
                if (i % 2 == 0) cards.add(new RiflemanCard(board, PieceAlignment.P2, 0, 0, 125, 200, 0));
                else cards.add(new RogueCard(board, PieceAlignment.P2, 0, 0, 125, 200, 0));
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
