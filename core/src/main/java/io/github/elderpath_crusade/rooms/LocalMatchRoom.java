package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.Deck;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.managers.DeckManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.managers.SettingsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class LocalMatchRoom extends BattleRoom {

    private LocalMatchRoom() {
        super(GameMode.LOCAL_MATCH);

        // LocalMatch-specific event listeners
        TypedEventBus.get().register(TurnStartedEvent.class, this::onTurnStarted);
        TypedEventBus.get().register(TurnEndedEvent.class, this::onTurnEnded);

        layoutBoard();

        // Initially show current player's hand
        hideAllHands();
        showPlayerHand(TurnManager.getCurrentPlayer());
    }

    @Override
    protected void onPassTurnClicked() {
        if (TurnManager.isWaitingForNextPlayer()) {
            TurnManager.startNextPlayerTurn();
        } else {
            TurnManager.endTurn();
        }
    }

    @Override
    protected Deck createDeck(PieceAlignment alignment, Hand hand) {
        List<Card> cards = new ArrayList<>();
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();

        if (DeckManager.hasDraftedDeck(alignment)) {
            List<Function<DeckManager.CardCreationParams, Card>> draftedDeck = DeckManager.getDraftedDeck(alignment);
            for (Function<DeckManager.CardCreationParams, Card> cardCreator : draftedDeck) {
                DeckManager.CardCreationParams params = new DeckManager.CardCreationParams(
                    board, alignment, 0, 0, 125, 200, 0
                );
                cards.add(cardCreator.apply(params));
            }
        }

        Deck deck = new Deck(cards, 0, 0, 125, 200, 1, SpriteBoxPos.BOTTOM_LEFT);
        deck.shuffle();
        deck.getBounds().setX(screenW - deck.getWidth() - 10);
        if (alignment == PieceAlignment.P1) {
            deck.getBounds().setY(10);
        } else {
            deck.getBounds().setY(screenH - deck.getHeight() - 10);
        }
        deck.setOwner(alignment);
        deck.setHand(hand);
        return deck;
    }

    private void onTurnStarted(TurnStartedEvent event) {
        PieceAlignment player = event.player();
        if (passTurn != null) passTurn.updateButtonText();
        swapHandPositionsIfNeeded(player);
        showPlayerHand(player);
    }

    private void onTurnEnded(TurnEndedEvent event) {
        if (passTurn != null) passTurn.updateButtonText();
        hideAllHands();
    }

    private void swapHandPositionsIfNeeded(PieceAlignment currentPlayer) {
        int screenH = SettingsManager.screenSize.getScreenHeight();
        int screenCenterX = SettingsManager.screenSize.getScreenCenter()[0];
        int p1HandBottomY = -80; // from BattleRoom init

        if (currentPlayer == PieceAlignment.P2) {
            handP1.setBottomY(screenH - handP1.getHeight());
            handP2.setBottomY(p1HandBottomY);
        } else {
            handP1.setBottomY(p1HandBottomY);
            handP2.setBottomY(screenH - handP2.getHeight());
        }
        handP1.setCenterX(screenCenterX);
        handP1.updateBounds();
        handP2.setCenterX(screenCenterX);
        handP2.updateBounds();
    }

    private void hideAllHands() {
        for (Card card : handP1.getCards()) card.showBack();
        for (Card card : handP2.getCards()) card.showBack();
    }

    private void showPlayerHand(PieceAlignment player) {
        Hand hand = (player == PieceAlignment.P1) ? handP1 : handP2;
        if (hand != null) {
            for (Card card : hand.getCards()) card.showFront();
        }
    }

    @Override
    public void onScreenResize() {
        super.onScreenResize();
        swapHandPositionsIfNeeded(TurnManager.getCurrentPlayer());
    }

    public static LocalMatchRoom get() {
        return new LocalMatchRoom();
    }
}
